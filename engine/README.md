# HARI Engine (experimental)

HARI Engine is a new, experimental browser-engine project being developed alongside Hari Browser. It is **not** the current production renderer and is not wired into the Android app.

## Implemented experimental pipeline

### Core input boundary (`engine/core/`)
A dependency-free Rust library that checks source byte length, UTF-8 validity, and forbidden control characters.

### HTML tokenizer/tree parser (`engine/html/`)
Supports nested elements, basic end-tag unwinding, comments, doctypes, common void elements, quoted/unquoted/boolean attributes, script/style raw text, and title/textarea RCDATA. It decodes numeric character references plus a small named-reference subset in text, attribute values, and RCDATA. It bounds document bytes, node count, nesting depth, and attributes per element.

### HARI DOM model (`engine/dom/`)
An arena-backed tree with stable node IDs, controlled append/reparent/remove operations, parent/child consistency checks, cycle prevention, a small document lifecycle (`Building`, `Ready`, `Closed`), and resource limits. Node relationships are private; mutations go through `DomDocument`.

### CSS subset and block-flow layout (`engine/layout/`)
A small stylesheet parser supports **type selectors only** and the declarations `display: block|none`, `width: Npx`, `height: Npx`, and `margin: Npx`. The layout pass computes deterministic rectangles using configurable fixed-width text metrics and a viewport width. Unknown properties and unsupported values are rejected rather than silently ignored.

### Software paint surface (`engine/paint/`)
A CPU-based painter consumes HARI layout boxes and fills a bounded RGBA8888 off-screen surface. It draws box outlines and text using a tiny built-in 5×7 bitmap glyph set; a single test runs the complete HTML → DOM → CSS/layout → paint pipeline and confirms pixel output. The rasterizer is deterministic and has an explicit pixel budget.

## Current limitations

This is an early prototype, not an HTML Living Standard implementation or production browser DOM. The parser's malformed-markup recovery is limited, and the named character-reference table is intentionally incomplete. Numeric references and a small set of common named references are handled; this is not full named-entity support. It does not execute JavaScript, fetch website resources, or implement the full CSS cascade, class/ID selectors, inline formatting contexts, CSS color/background declarations, images, font shaping, anti-aliasing, compositing, transforms, or device-scale behavior. The tiny bitmap font is only a test renderer.

Successful parsing or layout is not a content-safety or malware verdict. Never render untrusted documents in a privileged context based on these components alone.

## Next stages

1. **Expand browser conformance and hostile-input testing** — fuzzing, malformed markup, CSS edge cases, Unicode, resource-limit stress, and reference comparisons.
2. **Graphics improvements** — image decoding, real text metrics/font rasterization, color/background styles, clipping, and display scaling.
3. **JavaScript** — decide explicitly whether a maintained external runtime is required; disclose any reused runtime.
4. **Networking and storage** — origin model, TLS policy, cache/cookie isolation, safe redirects and downloads.
5. **Android integration** — a separate opt-in prototype behind an explicit feature boundary after the parser, layout, and paint work matures.

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
