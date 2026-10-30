# HARI Engine (experimental)

HARI Engine is a new, experimental browser-engine project being developed alongside Hari Browser. It is **not** the current production renderer and is not wired into the Android app.

## Implemented experimental pipeline

### Core input boundary (`engine/core/`)
A dependency-free Rust library that checks source byte length, UTF-8 validity, and forbidden control characters.

### HTML tokenizer/tree parser (`engine/html/`)
Supports nested elements, basic end-tag unwinding, comments, doctypes, common void elements, quoted/unquoted/boolean attributes, script/style raw text, and title/textarea RCDATA. It decodes numeric references and the complete bundled 2,231-spelling HTML5 named-reference table in text, attributes, and RCDATA, including legacy semicolonless names and multi-codepoint replacements. It bounds document bytes, node count, nesting depth, and attributes per element.

### HARI DOM model (`engine/dom/`)
An arena-backed tree with stable node IDs, controlled append/reparent/remove operations, parent/child consistency checks, cycle prevention, a small document lifecycle (`Building`, `Ready`, `Closed`), and resource limits. Node relationships are private; mutations go through `DomDocument`.

### CSS subset and block-flow layout (`engine/layout/`)
A bounded stylesheet parser supports type, universal, class, ID, compound, descendant, and child selectors (for example, `p.note`, `main p`, and `main > p`). Selector input is limited to 1 KiB and at most 16 compounds; selector identifiers and compound parts have additional limits. Attribute selectors, pseudo-classes, sibling combinators, and selector lists are unsupported. The supported declarations are `display: block|inline|none`, `width: Npx`, `height: Npx`, `margin: Npx`, `color`, and `background-color`. Colors are opaque and limited to `#RGB`, `#RRGGBB`, and a small set of named colors. The specificity-aware cascade applies supported rules; text color inherits through the tree, while element background color does not. The layout pass computes deterministic rectangles using configurable fixed-width text metrics and a viewport width, with limited inline flow and sibling line wrapping. Basic user-agent defaults suppress visible boxes for `base`, `head`, `link`, `meta`, `script`, `style`, `template`, and `title`; embedded CSS is not yet extracted from HTML. Unknown properties and unsupported values are rejected rather than silently ignored. Stylesheet input is limited to 256 KiB, 128 rules, and 64 declarations per rule; inputs exceeding these budgets are rejected.

### Software paint surface (`engine/paint/`)
A CPU-based painter consumes HARI layout boxes and fills a bounded RGBA8888 off-screen surface. It paints opaque CSS background rectangles before child content and draws text using a tiny built-in 5×7 bitmap glyph set. End-to-end tests run the HTML → DOM → CSS/layout → paint pipeline and check rendered color pixels. The rasterizer is deterministic and has an explicit pixel budget.

## Current limitations

This is an early prototype, not an HTML Living Standard implementation or production browser DOM. The parser's malformed-markup recovery remains limited; the named-reference matching algorithm is HARI-owned and uses bundled HTML5 reference data. It does not execute JavaScript or fetch website resources. CSS support is a deliberately bounded subset rather than the complete CSS cascade or selector model; text uses fixed-width metrics with simplified whitespace and line-breaking behavior, and inline layout is not standards-complete. The painter does not decode images, shape real fonts, anti-alias text, composite alpha, apply transforms, or account for device scale. The tiny bitmap font is only a test renderer.

Successful parsing or layout is not a content-safety or malware verdict. Never render untrusted documents in a privileged context based on these components alone.

## Next stages

1. **Expand browser conformance and hostile-input testing** — fuzzing, malformed markup, CSS edge cases, Unicode, resource-limit stress, and reference comparisons.
2. **Graphics improvements** — image decoding, real text metrics/font rasterization, clipping, alpha blending, and display scaling.
3. **JavaScript** — decide explicitly whether a maintained external runtime is required; disclose any reused runtime.
4. **Networking and storage** — origin model, TLS policy, cache/cookie isolation, safe redirects and downloads.
5. **Android integration** — a separate opt-in prototype behind an explicit feature boundary after the parser, layout, and paint work matures.

## Coverage-guided fuzzing

The separate `engine/fuzz/` package provides a `cargo-fuzz` target for the HTML parser. It uses a 16 KiB input ceiling and tighter node, depth, and per-element attribute budgets so fuzz runs stay bounded. Successful parses are traversed iteratively to check tree invariants. This is useful for finding crashes and invariant violations, but it is not a substitute for HTML conformance tests, long-running fuzz campaigns, or security review.

Install the tools with nightly Rust active, then run from the repository root:

```sh
cargo install cargo-fuzz --locked
cargo fuzz run --fuzz-dir=engine/fuzz html_parser -- -max_total_time=30 -max_len=16384
```

The `HARI Engine Fuzz Smoke` GitHub Actions workflow runs a bounded 30-second smoke test for engine changes and also supports scheduled and manual runs.

## Safety and compatibility rules

- The existing Android System WebView path remains the production path until the new engine passes compatibility, security, performance, and regression gates.
- Do not split `BrowserActivity.java` as part of this project.
- Do not edit, upload, or commit the private HARI Constitution.
- Do not weaken protected browser features to accommodate the prototype.
- Rust memory safety reduces some bug classes; it does not by itself make a browser secure.
- Every stage must have tests, documented limitations, and review before advancing.

## Local checks

From the repository root:

```sh
cargo fmt --manifest-path engine/core/Cargo.toml -- --check
cargo test --manifest-path engine/core/Cargo.toml
cargo clippy --manifest-path engine/core/Cargo.toml --all-targets -- -D warnings

cargo fmt --manifest-path engine/html/Cargo.toml -- --check
cargo test --manifest-path engine/html/Cargo.toml
cargo clippy --manifest-path engine/html/Cargo.toml --all-targets -- -D warnings

cargo fmt --manifest-path engine/dom/Cargo.toml -- --check
cargo test --manifest-path engine/dom/Cargo.toml
cargo clippy --manifest-path engine/dom/Cargo.toml --all-targets -- -D warnings

cargo fmt --manifest-path engine/layout/Cargo.toml -- --check
cargo test --manifest-path engine/layout/Cargo.toml
cargo clippy --manifest-path engine/layout/Cargo.toml --all-targets -- -D warnings

cargo fmt --manifest-path engine/paint/Cargo.toml -- --check
cargo test --manifest-path engine/paint/Cargo.toml
cargo clippy --manifest-path engine/paint/Cargo.toml --all-targets -- -D warnings
```

A dedicated GitHub Actions workflow runs these checks for engine changes. Android app build and tests remain separate from these engine checks.
