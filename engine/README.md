# HARI Engine (experimental)

HARI Engine is a new, experimental browser-engine project being developed alongside Hari Browser. It is **not** the current production renderer and is not wired into the app.

## First milestone

The first crate is `core/`, a dependency-free Rust library establishing a narrow document-input boundary. It currently checks:

- maximum source byte length (default 1 MiB);
- valid UTF-8;
- forbidden control characters, while allowing HTML whitespace (LF, CR, TAB).

These checks are not an HTML parser, sanitizer, malware detector, or complete security boundary. Untrusted input still requires robust parsing, resource limits throughout the pipeline, origin isolation, safe networking, and extensive adversarial testing.

## Planned stages

1. **Core and security contracts** — input limits, typed errors, deterministic behavior, test harness, threat model.
2. **HTML tokenizer/parser** — incremental parsing, bounded depth/nodes, malformed-input tests.
3. **DOM model** — ownership, mutation rules, document lifecycle.
4. **CSS subset and layout** — start with a deliberately small, documented subset.
5. **Text and graphics output** — bounded rendering and deterministic visual fixtures.
6. **Networking and storage** — origin model, TLS policy, cache/cookie isolation, safe redirects and downloads.
7. **JavaScript** — decide explicitly whether a maintained external runtime is required; disclose any reuse rather than claiming it was built from scratch.
8. **Android integration** — a separate opt-in prototype behind an explicit feature boundary.

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
```

The repository's Android build and tests remain separate from these engine checks.
