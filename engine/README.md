# HARI Engine (experimental)

HARI Engine is a new, experimental browser-engine project being developed alongside Hari Browser. It is **not** the current production renderer and is not wired into the Android app.

## Implemented foundations

### Core input boundary (`engine/core/`)
A dependency-free Rust library that checks:

- maximum source byte length (default 1 MiB);
- valid UTF-8;
- forbidden control characters, while allowing normal HTML whitespace (LF, CR, TAB).

### Initial HTML tokenizer/tree parser (`engine/html/`)
The parser currently supports:

- nested element nodes and basic end-tag unwinding;
- HTML comments and doctypes;
- quoted, unquoted, and boolean attributes;
- common void elements such as `img`, `br`, and `meta`;
- raw-text handling for `script`, `style`, `textarea`, and `title`;
- configurable byte, node, nesting-depth, and per-element attribute limits;
- deterministic errors for rejected input.

It builds a simple HARI-owned tree, **not a standards-compliant browser DOM**. It does not decode HTML character references, execute JavaScript, fetch resources, implement CSS, lay out pages, or render pixels. Malformed-HTML recovery is intentionally limited and will need a much larger conformance test suite before it can be used for real browsing.

Successful parsing is not a content-safety or malware verdict. Do not render untrusted documents in a privileged context based on this parser alone.

## Next stages

1. **Expand parser tests and tokenizer behavior** — malformed inputs, duplicate attributes, comments, raw-text cases, Unicode, and resource-limit stress cases.
2. **DOM model** — ownership, mutation rules, document lifecycle, parent/child invariants.
3. **CSS subset and layout** — start with a small, explicitly documented subset.
4. **Text and graphics output** — bounded rendering and deterministic visual fixtures.
5. **Networking and storage** — origin model, TLS policy, cache/cookie isolation, safe redirects and downloads.
6. **JavaScript** — make an explicit decision about whether a maintained external runtime is required; disclose all reuse.
7. **Android integration** — separate opt-in prototype behind an explicit feature boundary.

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
```

A dedicated GitHub Actions workflow runs these checks for engine changes. The Android app's existing build and tests remain separate.
