# HARI Engine (experimental)

HARI Engine is a new, experimental browser-engine project being developed alongside Hari Browser. It is **not** the current production renderer and is not wired into the Android app.

## Implemented experimental pipeline

### Core input boundary (`engine/core/`)
A dependency-free Rust library that checks source byte length, UTF-8 validity, and forbidden control characters.

### HTML tokenizer/tree parser (`engine/html/`)
The first parser supports nested elements, basic end-tag unwinding, comments, doctypes, common void elements, quoted/unquoted/boolean attributes, and raw-text handling for `script`, `style`, `textarea`, and `title`. It bounds document bytes, node count, nesting depth, and attributes per element.

### HARI DOM model (`engine/dom/`)
An arena-backed tree with stable node IDs, controlled append/reparent/remove operations, parent/child consistency checks, cycle prevention, a small document lifecycle (`Building`, `Ready`, `Closed`), and resource limits. Node relationships are private; mutations go through `DomDocument`.

### CSS subset and block-flow layout (`engine/layout/`)
A small stylesheet parser supports **type selectors only** and the declarations `display: block|none`, `width: Npx`, `height: Npx`, and `margin: Npx`. The layout pass computes deterministic rectangles using configurable fixed-width text metrics and a viewport width. Unknown properties and unsupported values are rejected rather than silently ignored.

This layout result is **not a pixel renderer**. It does not implement the CSS cascade in full, selectors beyond tag names, inline formatting contexts, font shaping, whitespace collapsing, images, painting, compositing, or device-scale behavior.

## Current limitations

This is an early prototype, not an HTML Living Standard implementation or production browser DOM. It does not execute JavaScript, fetch website resources, decode HTML character references, provide a full CSS engine, or render pixels. Malformed markup recovery is intentionally limited and requires extensive conformance/fuzz testing before any real browsing use.

Successful parsing or layout is not a content-safety or malware verdict. Never render untrusted documents in a privileged context based on these components alone.

## Next stages

1. **Paint/output surface** — turn layout boxes into a basic deterministic drawing/display list, then a testable off-screen image or Android surface.
2. **Broaden parser and layout tests** — malformed input, CSS edge cases, Unicode, resource-limit stress, fuzzing and reference comparisons.
3. **JavaScript** — decide explicitly whether a maintained external runtime is required; disclose any reused runtime.
4. **Networking and storage** — origin model, TLS policy, cache/cookie isolation, safe redirects and downloads.
5. **Android integration** — a separate opt-in prototype behind an explicit feature boundary.

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
```

A dedicated GitHub Actions workflow runs these checks for engine changes. Android app build and tests remain separate from these engine checks.
