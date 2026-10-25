# Hari URL Guard (experimental)

This small Rust crate is a pilot for the Java/Kotlin-to-Rust boundary in Hari
Browser. It uses the Rust `url` parser and exports one JNI method that returns
a URL-scheme classification.

## Scope and limitations

The result only classifies syntax and URL scheme:

- `HTTPS`: syntactically accepted HTTPS URL with a host.
- `HTTP`: syntactically accepted HTTP URL with a host.
- `OTHER`: another parsed absolute scheme, such as `mailto:`.
- `INVALID`: invalid or deliberately rejected input.

**This is not website reputation, phishing detection, malware scanning, a TLS
check, or a decision that a URL is safe.** Do not allow navigation solely
because the result is `HTTPS`.

The Rust library is optional in ordinary Android builds. The Kotlin adapter
reports which backend answered and uses a Kotlin fallback when the native
library is absent or cannot be called.

## Tests

```sh
cargo test --manifest-path rust/hari_url_guard/Cargo.toml
cargo fmt --manifest-path rust/hari_url_guard/Cargo.toml -- --check
./gradlew testDebugUnitTest
```

## Optional Android native build

Install Rust, the Android NDK, the Android targets, and `cargo-ndk` first:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk --locked
./gradlew --no-daemon assembleDebug -PenableRustNativeBuild=true
```

The Gradle task packages `libhari_url_guard.so` for ARM64, ARMv7 and x86_64.
A real Android-device smoke test is still required before treating the JNI path
as runtime-verified.
