//! HARI-owned local search indexing and retrieval core.
//!
//! This crate deliberately performs no network access. A separately reviewed
//! crawler/service will be responsible for discovering and supplying pages.

mod snapshot;

pub use snapshot::SnapshotError;

use std::collections::{BTreeMap, BTreeSet};
use std::fmt;

use url::Url;

const MAX_DOCUMENTS: usize = 50_000;
const MAX_INDEX_SOURCE_BYTES: usize = 16 * 1024 * 1024;
const MAX_INDEX_TOKENS: usize = 250_000;
const MAX_ID_BYTES: usize = 128;
const MAX_URL_BYTES: usize = 4_096;
const MAX_TITLE_BYTES: usize = 1_024;
const MAX_BODY_BYTES: usize = 262_144;
const MAX_DOCUMENT_TOKENS: usize = 50_000;
const MAX_QUERY_BYTES: usize = 4_096;
const MAX_QUERY_TOKENS: usize = 256;
const MAX_RESULTS: usize = 100;

const TITLE_WEIGHT: f64 = 2.5;
const BM25_K1: f64 = 1.2;
const BM25_B: f64 = 0.75;

/// A page or document supplied by a future HARI crawler/importer.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Document {
    pub id: String,
    pub url: String,
    pub title: String,
    pub body: String,
}

/// One ranked result returned by SearchIndex::search.
#[derive(Debug, Clone, PartialEq)]
pub struct SearchHit {
    pub document_id: String,
    pub url: String,
    pub title: String,
    /// BM25 relevance score. It is useful for ordering, not a probability.
    pub score: f64,
}

/// Errors returned when a document cannot be added to the bounded index.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IndexError {
    InvalidId,
    InvalidUrl,
    DocumentTooLarge,
    TooManyDocuments,
    IndexCapacityExceeded,
}

impl fmt::Display for IndexError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let message = match self {
            Self::InvalidId => "document ID is empty, padded, or too long",
            Self::InvalidUrl => {
                "document URL must be a valid HTTP or HTTPS URL without credentials"
            }
            Self::DocumentTooLarge => "document exceeds an indexing size or token limit",
            Self::TooManyDocuments => "the configured document-count limit has been reached",
            Self::IndexCapacityExceeded => {
                "the configured aggregate index capacity has been reached"
            }
        };
        formatter.write_str(message)
    }
}

impl std::error::Error for IndexError {}

#[derive(Debug, Clone, Copy, Default)]
struct Posting {
    title_frequency: u32,
    body_frequency: u32,
}

struct StoredDocument {
    document: Document,
    title_terms: BTreeMap<String, u32>,
    body_terms: BTreeMap<String, u32>,
    title_length: usize,
    body_length: usize,
    source_bytes: usize,
}

/// An in-memory inverted index with bounded input and deterministic BM25 ranking.
///
/// Documents are updated by ID. Replacing or removing a document also removes
/// its old postings, so stale terms cannot continue to produce results.
#[derive(Default)]
pub struct SearchIndex {
    documents: BTreeMap<String, StoredDocument>,
    postings: BTreeMap<String, BTreeMap<String, Posting>>,
    total_title_tokens: usize,
    total_body_tokens: usize,
    total_source_bytes: usize,
}

impl SearchIndex {
    /// Creates an empty search index.
    pub fn new() -> Self {
        Self::default()
    }

    /// Returns the number of documents currently indexed.
    pub fn document_count(&self) -> usize {
        self.documents.len()
    }

    /// Adds a document or replaces the existing document with the same ID.
    ///
    /// Only HTTP and HTTPS URLs are accepted. The function does not fetch,
    /// crawl, or certify the destination; it only validates the URL syntax
    /// before storing it as a result link.
    pub fn add_document(&mut self, document: Document) -> Result<(), IndexError> {
        if document.id.trim().is_empty()
            || document.id.trim() != document.id
            || document.id.len() > MAX_ID_BYTES
        {
            return Err(IndexError::InvalidId);
        }

        if document.url.is_empty()
            || document.url.len() > MAX_URL_BYTES
            || document
                .url
                .chars()
                .any(|character| character.is_whitespace() || character.is_control())
        {
            return Err(IndexError::InvalidUrl);
        }

        let parsed_url = Url::parse(&document.url).map_err(|_| IndexError::InvalidUrl)?;
        if !matches!(parsed_url.scheme(), "http" | "https")
            || parsed_url.host_str().is_none()
            || !parsed_url.username().is_empty()
            || parsed_url.password().is_some()
        {
            return Err(IndexError::InvalidUrl);
        }

        if document.title.len() > MAX_TITLE_BYTES || document.body.len() > MAX_BODY_BYTES {
            return Err(IndexError::DocumentTooLarge);
        }

        let (title_terms, title_length) = token_frequencies(&document.title, MAX_DOCUMENT_TOKENS)
            .ok_or(IndexError::DocumentTooLarge)?;
        let body_token_limit = MAX_DOCUMENT_TOKENS.saturating_sub(title_length);
        let (body_terms, body_length) = token_frequencies(&document.body, body_token_limit)
            .ok_or(IndexError::DocumentTooLarge)?;

        let existing = self.documents.get(&document.id);
        let replacing_existing = existing.is_some();
        if !replacing_existing && self.documents.len() >= MAX_DOCUMENTS {
            return Err(IndexError::TooManyDocuments);
        }

        let existing_tokens = existing
            .map(|stored| stored.title_length + stored.body_length)
            .unwrap_or(0);
        let existing_bytes = existing.map(|stored| stored.source_bytes).unwrap_or(0);
        let new_tokens = title_length + body_length;
        let new_source_bytes =
            document.id.len() + document.url.len() + document.title.len() + document.body.len();

        let remaining_tokens = self.total_title_tokens + self.total_body_tokens - existing_tokens;
        let remaining_bytes = self.total_source_bytes - existing_bytes;
        if remaining_tokens + new_tokens > MAX_INDEX_TOKENS
            || remaining_bytes + new_source_bytes > MAX_INDEX_SOURCE_BYTES
        {
            return Err(IndexError::IndexCapacityExceeded);
        }

        let document_id = document.id.clone();
        let stored = StoredDocument {
            document,
            title_terms,
            body_terms,
            title_length,
            body_length,
            source_bytes: new_source_bytes,
        };

        if replacing_existing {
            self.remove_document(&document_id);
        }

        self.total_title_tokens += stored.title_length;
        self.total_body_tokens += stored.body_length;
        self.total_source_bytes += stored.source_bytes;

        let mut document_terms: BTreeSet<String> = stored.title_terms.keys().cloned().collect();
        document_terms.extend(stored.body_terms.keys().cloned());

        for term in document_terms {
            let posting = Posting {
                title_frequency: stored.title_terms.get(&term).copied().unwrap_or(0),
                body_frequency: stored.body_terms.get(&term).copied().unwrap_or(0),
            };
            self.postings
                .entry(term)
                .or_default()
                .insert(document_id.clone(), posting);
        }

        self.documents.insert(document_id, stored);
        Ok(())
    }

    /// Removes a document and all of its inverted-index postings.
    ///
    /// Returns true only when a document with the supplied ID existed.
    pub fn remove_document(&mut self, document_id: &str) -> bool {
        let Some(stored) = self.documents.remove(document_id) else {
            return false;
        };

        self.total_title_tokens -= stored.title_length;
        self.total_body_tokens -= stored.body_length;
        self.total_source_bytes -= stored.source_bytes;

        let mut document_terms: BTreeSet<String> = stored.title_terms.keys().cloned().collect();
        document_terms.extend(stored.body_terms.keys().cloned());

        let mut empty_terms = Vec::new();
        for term in document_terms {
            let remove_term = if let Some(term_postings) = self.postings.get_mut(&term) {
                term_postings.remove(document_id);
                term_postings.is_empty()
            } else {
                false
            };

            if remove_term {
                empty_terms.push(term);
            }
        }

        for term in empty_terms {
            self.postings.remove(&term);
        }

        true
    }

    /// Searches indexed documents using BM25 with a title-field boost.
    ///
    /// Results are ordered by descending score and then ascending document ID
    /// for deterministic ties. At most 100 results are returned.
    pub fn search(&self, query: &str, limit: usize) -> Vec<SearchHit> {
        if limit == 0 || query.len() > MAX_QUERY_BYTES || self.documents.is_empty() {
            return Vec::new();
        }

        let Some((query_terms, _)) = token_frequencies(query, MAX_QUERY_TOKENS) else {
            return Vec::new();
        };
        if query_terms.is_empty() {
            return Vec::new();
        }

        let document_count = self.documents.len() as f64;
        let average_length = (self.total_body_tokens as f64
            + self.total_title_tokens as f64 * TITLE_WEIGHT)
            / document_count;
        let average_length = if average_length > f64::EPSILON {
            average_length
        } else {
            1.0
        };

        let mut scores: BTreeMap<String, f64> = BTreeMap::new();

        for term in query_terms.keys() {
            let Some(term_postings) = self.postings.get(term) else {
                continue;
            };

            let document_frequency = term_postings.len() as f64;
            let inverse_document_frequency = (1.0
                + (document_count - document_frequency + 0.5) / (document_frequency + 0.5))
                .ln();

            for (document_id, posting) in term_postings {
                let Some(stored) = self.documents.get(document_id) else {
                    continue;
                };

                let weighted_frequency =
                    posting.body_frequency as f64 + posting.title_frequency as f64 * TITLE_WEIGHT;
                let document_length =
                    stored.body_length as f64 + stored.title_length as f64 * TITLE_WEIGHT;
                let normalization = weighted_frequency
                    + BM25_K1 * (1.0 - BM25_B + BM25_B * document_length / average_length);

                if normalization <= 0.0 {
                    continue;
                }

                let term_score = inverse_document_frequency
                    * (weighted_frequency * (BM25_K1 + 1.0) / normalization);
                if term_score.is_finite() && term_score > 0.0 {
                    *scores.entry(document_id.clone()).or_insert(0.0) += term_score;
                }
            }
        }

        let mut results = Vec::with_capacity(scores.len());
        for (document_id, score) in scores {
            if !score.is_finite() || score <= 0.0 {
                continue;
            }
            if let Some(stored) = self.documents.get(&document_id) {
                results.push(SearchHit {
                    document_id,
                    url: stored.document.url.clone(),
                    title: stored.document.title.clone(),
                    score,
                });
            }
        }

        results.sort_by(|left, right| {
            right
                .score
                .total_cmp(&left.score)
                .then_with(|| left.document_id.cmp(&right.document_id))
        });
        results.truncate(limit.min(MAX_RESULTS));
        results
    }
}

fn token_frequencies(text: &str, max_tokens: usize) -> Option<(BTreeMap<String, u32>, usize)> {
    let mut frequencies = BTreeMap::new();
    let mut token = String::new();
    let mut token_count = 0;

    for character in text.chars() {
        if character.is_alphanumeric() || (!token.is_empty() && is_combining_mark(character)) {
            token.extend(character.to_lowercase());
        } else if !push_token(&mut token, &mut frequencies, &mut token_count, max_tokens) {
            return None;
        }
    }

    if !push_token(&mut token, &mut frequencies, &mut token_count, max_tokens) {
        return None;
    }

    Some((frequencies, token_count))
}

fn push_token(
    token: &mut String,
    frequencies: &mut BTreeMap<String, u32>,
    token_count: &mut usize,
    max_tokens: usize,
) -> bool {
    if token.is_empty() {
        return true;
    }
    if *token_count >= max_tokens {
        return false;
    }

    let word = std::mem::take(token);
    *frequencies.entry(word).or_insert(0) += 1;
    *token_count += 1;
    true
}

/// Keep combining marks attached to the preceding letter in common scripts.
/// This avoids splitting vowel signs and viramas into separate search terms.
fn is_combining_mark(character: char) -> bool {
    matches!(
        character as u32,
        0x0300..=0x036F
            | 0x0483..=0x0489
            | 0x0591..=0x05BD
            | 0x05BF
            | 0x05C1..=0x05C2
            | 0x05C4..=0x05C5
            | 0x05C7
            | 0x0610..=0x061A
            | 0x064B..=0x065F
            | 0x0670
            | 0x06D6..=0x06ED
            | 0x0900..=0x0903
            | 0x093A..=0x094F
            | 0x0951..=0x0957
            | 0x0962..=0x0963
            | 0x0981..=0x0983
            | 0x09BC..=0x09CD
            | 0x09D7
            | 0x0A01..=0x0A03
            | 0x0A3C
            | 0x0A3E..=0x0A4D
            | 0x0ABC
            | 0x0ABE..=0x0ACD
            | 0x0B01..=0x0B03
            | 0x0B3C..=0x0B4D
            | 0x0B56..=0x0B57
            | 0x0BBE..=0x0BCD
            | 0x0BD7
            | 0x0C00..=0x0C04
            | 0x0C3E..=0x0C56
            | 0x0C62..=0x0C63
            | 0x0C81..=0x0C83
            | 0x0CBC..=0x0CD6
            | 0x0CE2..=0x0CE3
            | 0x0D00..=0x0D03
            | 0x0D3B..=0x0D4D
            | 0x0D57
            | 0x0D62..=0x0D63
            | 0x0D81..=0x0D83
            | 0x0DCA
            | 0x0DCF..=0x0DD6
            | 0x0DD8..=0x0DDF
            | 0x0DF2..=0x0DF3
            | 0x0E31
            | 0x0E34..=0x0E3A
            | 0x0E47..=0x0E4E
            | 0x0EB1
            | 0x0EB4..=0x0EBC
            | 0x0EC8..=0x0ECD
            | 0x1AB0..=0x1AFF
            | 0x1DC0..=0x1DFF
            | 0x20D0..=0x20FF
            | 0xFE20..=0xFE2F
    )
}

// Acceptance tests for the first HARI-owned search-index milestone.
// They were added before the implementation (TDD test-first stage).

#[cfg(test)]
mod tests {
    use super::{Document, IndexError, SearchIndex};

    fn document(id: &str, url: &str, title: &str, body: &str) -> Document {
        Document {
            id: id.to_owned(),
            url: url.to_owned(),
            title: title.to_owned(),
            body: body.to_owned(),
        }
    }

    #[test]
    fn returns_relevant_documents_with_title_matches_boosted() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "title-match",
                "https://example.com/kerala",
                "Kerala history",
                "regional culture and archives",
            ))
            .unwrap();
        index
            .add_document(document(
                "body-match",
                "https://example.com/travel",
                "A travel journal",
                "Kerala history Kerala history and local archives",
            ))
            .unwrap();

        let hits = index.search("kerala history", 10);

        assert_eq!(2, hits.len());
        assert_eq!("title-match", hits[0].document_id);
        assert!(hits[0].score > hits[1].score);
    }

    #[test]
    fn searches_case_insensitively_and_supports_malayalam_tokens() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "space",
                "https://example.com/space",
                "Space exploration",
                "Astronomy for beginners",
            ))
            .unwrap();
        index
            .add_document(document(
                "malayalam",
                "https://example.com/keralam",
                "കേരള ചരിത്രം",
                "കേരളത്തിന്റെ സംസ്കാരവും ചരിത്രവും",
            ))
            .unwrap();

        assert_eq!("space", index.search("SPACE EXPLORATION", 5)[0].document_id);
        assert_eq!("malayalam", index.search("കേരള ചരിത്രം", 5)[0].document_id);
    }

    #[test]
    fn empty_or_punctuation_only_queries_return_no_results() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "one",
                "https://example.com/",
                "A useful page",
                "searchable words",
            ))
            .unwrap();

        assert!(index.search("", 10).is_empty());
        assert!(index.search("   !!! --- ", 10).is_empty());
        assert!(index.search("unmatched", 10).is_empty());
        assert!(index.search("useful", 0).is_empty());
    }

    #[test]
    fn replacing_a_document_removes_its_old_terms_without_duplicating_it() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "same-id",
                "https://example.com/old",
                "Old astronomy article",
                "stars and planets",
            ))
            .unwrap();
        index
            .add_document(document(
                "same-id",
                "https://example.com/new",
                "New ocean article",
                "coral reefs and oceans",
            ))
            .unwrap();

        assert_eq!(1, index.document_count());
        assert!(index.search("astronomy stars planets", 10).is_empty());
        let hits = index.search("ocean coral", 10);
        assert_eq!(1, hits.len());
        assert_eq!("https://example.com/new", hits[0].url);
    }

    #[test]
    fn removing_a_document_removes_all_of_its_search_terms() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "remove-me",
                "https://example.com/",
                "Ancient mathematics",
                "number theory",
            ))
            .unwrap();

        assert!(index.remove_document("remove-me"));
        assert!(!index.remove_document("remove-me"));
        assert_eq!(0, index.document_count());
        assert!(index.search("mathematics number", 10).is_empty());
    }

    #[test]
    fn invalid_documents_are_rejected_without_mutating_the_index() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "valid",
                "https://example.com/",
                "Valid document",
                "known searchable term",
            ))
            .unwrap();

        let empty_id = index.add_document(document(
            "   ",
            "https://example.com/invalid",
            "Invalid",
            "body",
        ));
        assert_eq!(Err(IndexError::InvalidId), empty_id);

        let empty_url = index.add_document(document("bad-url", " ", "Invalid", "body"));
        assert_eq!(Err(IndexError::InvalidUrl), empty_url);

        assert_eq!(1, index.document_count());
        assert_eq!("valid", index.search("known", 5)[0].document_id);
    }

    #[test]
    fn oversized_document_content_is_rejected_without_partial_indexing() {
        let mut index = SearchIndex::new();
        let oversized = "x".repeat(300_000);

        assert_eq!(
            Err(IndexError::DocumentTooLarge),
            index.add_document(document(
                "too-large",
                "https://example.com/large",
                "Large page",
                &oversized,
            ))
        );
        assert_eq!(0, index.document_count());
        assert!(index.search("x", 5).is_empty());
    }

    #[test]
    fn equal_scores_have_a_stable_document_id_order() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "doc-b",
                "https://example.com/b",
                "shared term",
                "same content",
            ))
            .unwrap();
        index
            .add_document(document(
                "doc-a",
                "https://example.com/a",
                "shared term",
                "same content",
            ))
            .unwrap();

        let hits = index.search("shared", 10);

        assert_eq!(2, hits.len());
        assert_eq!("doc-a", hits[0].document_id);
        assert_eq!("doc-b", hits[1].document_id);
    }
    #[test]
    fn rejects_non_web_and_credential_bearing_urls_without_replacing_existing_data() {
        let mut index = SearchIndex::new();
        index
            .add_document(document(
                "protected",
                "https://example.com/original",
                "Original astronomy page",
                "stars planets",
            ))
            .unwrap();

        let ftp = index.add_document(document(
            "ftp",
            "ftp://example.com/file",
            "FTP page",
            "not web content",
        ));
        assert_eq!(Err(IndexError::InvalidUrl), ftp);

        let credentials = index.add_document(document(
            "protected",
            "https://user:password@example.com/replacement",
            "Replacement",
            "different content",
        ));
        assert_eq!(Err(IndexError::InvalidUrl), credentials);

        let hits = index.search("astronomy stars", 5);
        assert_eq!(1, hits.len());
        assert_eq!("https://example.com/original", hits[0].url);
        assert_eq!("Original astronomy page", hits[0].title);
    }

    #[test]
    fn enforces_an_aggregate_index_token_budget_without_partial_insertions() {
        let mut index = SearchIndex::new();
        let repeated_terms = "x ".repeat(12_499);

        for number in 0..20 {
            index
                .add_document(document(
                    &format!("doc-{number:02}"),
                    &format!("https://example.com/{number}"),
                    "Title",
                    &repeated_terms,
                ))
                .unwrap();
        }

        assert_eq!(20, index.document_count());

        let over_budget = index.add_document(document(
            "over-budget",
            "https://example.com/over-budget",
            "Title",
            "x",
        ));
        assert_eq!(Err(IndexError::IndexCapacityExceeded), over_budget);
        assert_eq!(20, index.document_count());
        assert_eq!(20, index.search("x", 100).len());
    }

    #[test]
    fn caps_returned_results_even_when_the_requested_limit_is_larger() {
        let mut index = SearchIndex::new();
        for number in 0..105 {
            index
                .add_document(document(
                    &format!("doc-{number:03}"),
                    &format!("https://example.com/{number}"),
                    "Common astronomy",
                    "common learning resource",
                ))
                .unwrap();
        }

        assert_eq!(100, index.search("common", usize::MAX).len());
    }
    #[test]
    fn enforces_the_aggregate_source_byte_budget() {
        let mut index = SearchIndex::new();
        let large_body = "z".repeat(262_144);

        for number in 0..63 {
            index
                .add_document(document(
                    &format!("large-{number:02}"),
                    &format!("https://example.com/large/{number}"),
                    "Large",
                    &large_body,
                ))
                .unwrap();
        }

        assert_eq!(63, index.document_count());
        let over_budget = index.add_document(document(
            "large-63",
            "https://example.com/large/63",
            "Large",
            &large_body,
        ));
        assert_eq!(Err(IndexError::IndexCapacityExceeded), over_budget);
        assert_eq!(63, index.document_count());
        assert_eq!(63, index.search("large", 100).len());
    }
}
