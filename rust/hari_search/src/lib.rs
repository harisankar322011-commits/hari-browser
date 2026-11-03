//! Acceptance tests for the first HARI-owned search-index milestone.
//! These tests intentionally precede the implementation (TDD RED stage).

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
        assert_eq!(
            "malayalam",
            index.search("കേരള ചരിത്രം", 5)[0].document_id
        );
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
}
