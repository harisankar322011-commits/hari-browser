# HARI Search Core

## Current status

**Stage 1 prototype — local indexing and ranking core.**

`hari_search` is an HARI-owned Rust library for indexing documents supplied by another HARI component and returning ranked results. It uses an inverted term index and BM25 scoring, with an additional title-field weight. It performs no network requests and does not depend on an external search index.

This is a foundation, not yet a public-web search engine. It has no crawler, persistent index, hosted query API, HTML extraction pipeline, browser UI integration, or production corpus.

## What exists in this stage

- Add, replace, and remove documents by stable ID.
- Search indexed title and body text with deterministic BM25 ranking.
- Prefer title matches over otherwise comparable body-only matches.
- Case-fold terms and preserve combining marks for selected Indic and other scripts, including Malayalam examples.
- Return stable ordering for equal scores.
- Reject invalid IDs, non-HTTP(S) result URLs, URLs containing credentials, and oversized documents.
- Apply per-document limits, a 50,000-document limit, a 16 MiB aggregate source-text limit, a 250,000-token aggregate limit, a 4,096-byte query limit, a 256-token query limit, and a 100-result maximum.
- Keep the implementation network-free. Adding a document never causes a URL to be fetched.

The configured bounds are initial engineering limits and must be measured and tuned before large-scale indexing.

## Build and test

From the repository root:

```sh
cargo fmt --manifest-path rust/hari_search/Cargo.toml -- --check
cargo test --manifest-path rust/hari_search/Cargo.toml
cargo clippy --manifest-path rust/hari_search/Cargo.toml --all-targets -- -D warnings
```

The dedicated GitHub Actions workflow runs these checks for changes to this crate. The snapshot hostile-input integration suite exercises every truncated prefix and every single-bit mutation of a valid sample, malformed declared counts and lengths, checksum-preserving invalid UTF-8, and the reader's maximum byte-consumption boundary.

A separate `cargo-fuzz` target, `snapshot_decode`, exercises arbitrary byte streams and mutations of a known-valid snapshot. The HARI Search Snapshot Fuzz Smoke workflow runs a bounded 30-second campaign with a 16 KiB input ceiling. This is crash-resistance and parser-path exploration, not exhaustive fuzzing, cryptographic authentication, or proof of production security.

## Known limitations

- The index is in memory only. Restarting the process loses every document.
- Tokenization is deliberately basic. It does not yet provide Unicode normalization, stemming, stop-word policies, typo tolerance, phrase search, full language-specific morphology, or robust segmentation for every writing system.
- BM25 is the initial lexical ranker. It is not semantic ranking and does not prove that a result is authoritative, safe, current, or correct.
- There is no HTML parser/extractor in this crate. The future ingestion component must produce bounded, reviewed plain-text fields.
- Search results cover only documents explicitly added to this process. No general web coverage is implied.

## Roadmap and promotion gates

### Stage 1 — Local search core

Implement and test bounded indexing, document updates/deletes, multilingual-safe token boundaries, deterministic BM25 ranking, and result limits. Keep it isolated from the Android production navigation path.

### Stage 2 — Ingestion and persistent storage

**Current incremental milestone: versioned local snapshot codec plus hostile-input fuzz infrastructure.** The prototype can write a deterministic snapshot of indexed source documents and rebuild the in-memory inverted index from a validated snapshot. The format has a magic header, explicit version and flags, document count, payload length, and an FNV-1a checksum for accidental-corruption detection. Loading is bounded, rejects malformed/truncated/trailing data, unsupported versions, duplicate IDs, invalid URLs, and documents that exceed the normal indexing budgets. The checksum is not authentication or tamper protection; the caller must keep snapshots in app-private storage and use a safe replacement strategy.

The decoder now has deterministic hostile-input regression tests and a coverage-guided fuzz target. This is not yet a production persistent store: there is no automatic file location, encryption, crash-safe transaction manager, incremental write path, backup/restore UI, or browser integration. Next work is to specify atomic file lifecycle and recovery behavior, measure memory and I/O costs, and only then consider HTML-to-text ingestion, canonical URL handling, content hashing, and duplicate detection.

### Stage 3 — Controlled web crawler

Only after a separate security review, add a crawler with:
- robots.txt and site-policy handling, a clear crawler identity, and a published contact/opt-out path;
- strict per-host rate limits, concurrency ceilings, timeouts, bounded retries, and exponential backoff;
- HTTPS-first handling, bounded redirects, content-type and response-size limits;
- SSRF protection that validates resolved IP addresses and every redirect, and blocks loopback, private, link-local, multicast, and cloud-metadata destinations;
- no login, authenticated, private, paywalled, or user-specific pages; no collection of cookies or credentials;
- canonicalization, deduplication, crawl budgets, recrawl intervals, abuse reporting, and an emergency crawler shutdown switch.

Robots.txt is a crawler coordination mechanism, not a blanket legal permission. Site terms, applicable law, copyright, privacy, and reasonable site load still require review.

### Stage 4 — Search quality and safety

Build language-aware analysis, spam resistance, freshness and source-quality signals, safe-search controls, transparent result explanations, and quality evaluation using human-reviewed query sets. Measure relevance, latency, coverage, freshness, and false-positive rates instead of claiming quality without evidence.

### Stage 5 — HARI Search service

Design a secure hosted query API, index distribution/update process, availability and abuse controls, rate limits, monitoring, incident response, and explicit data-retention policy. Search queries are sensitive: minimize collection, never use browser history as training data by default, and define how ordinary and private browsing interact with any remote service before enabling it.

### Stage 6 — Hari Browser integration

Add HARI Search as an optional provider only after the service, index, privacy behavior, safety controls, and device testing are ready. Keep existing providers and production navigation behavior intact until an explicit, tested migration is approved. Private mode must not silently persist or expose queries.

## Architecture boundary

The HARI HTML/rendering engine and HARI Search are separate systems:

- **HARI HTML/rendering engine:** parses and eventually renders web documents.
- **HARI Search:** ingests approved document text, builds an index, ranks query matches, and later serves search results.

A parser improvement does not create a crawler or search corpus. A search index does not create a browser renderer. Each subsystem needs its own tests, threat model, benchmarks, and release gates.
