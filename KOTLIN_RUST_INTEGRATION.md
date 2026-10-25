# HARI Browser Kotlin + Rust Integration Pilot

## Status

This is a separate-branch prototype. The existing Java browser and the
`main` branch remain unchanged. The Kotlin/Rust classifier is wired into WebView main-frame navigation and
main-frame request interception as a strict URL-input guard. It rejects
malformed or ambiguous HTTP(S) syntax, bad ports, malformed percent escapes,
and oversized URLs before a main-frame request proceeds. Valid internationalized
domain names are supported. A Rust/Kotlin parser disagreement fails closed.

This is still separate from website reputation, TLS validation, page-content
analysis, and download/file malware scanning.

## Why Kotlin is configured this way

The current project uses Android Gradle Plugin 9.4.1. AGP 9 provides built-in
Kotlin support, so the app enables that support directly rather than applying
the older `org.jetbrains.kotlin.android` plugin. See the Android documentation:
https://developer.android.com/build/migrate-to-built-in-kotlin

Java remains the primary existing implementation. Kotlin is being introduced
additively, so existing Java files need not be rewritten.

## Rust boundary

The pilot crate is at `rust/hari_url_guard`. It exposes a small classifier
through JNI. The Kotlin adapter first tries to load `libhari_url_guard.so`;
when absent, it uses its Kotlin fallback and reports that backend in the
result. Ordinary builds do not require Rust or the NDK.

The native build is opt-in:

```sh
./gradlew --no-daemon testDebugUnitTest assembleDebug -PenableRustNativeBuild=true
cargo test --manifest-path rust/hari_url_guard/Cargo.toml
cargo fmt --manifest-path rust/hari_url_guard/Cargo.toml -- --check
```

Build prerequisites for the native task are a compatible Rust toolchain, the
Android NDK, Android targets `aarch64-linux-android`,
`armv7-linux-androideabi`, and `x86_64-linux-android`, plus `cargo-ndk`.
The task places generated shared libraries under the app's build directory;
it does not commit compiled binaries.

## Verification gates before merging or using in production

1. Kotlin unit tests pass.
2. Rust unit tests and formatting checks pass.
3. CI builds the APK with native compilation enabled and verifies the packaged
   native libraries for all three configured ABIs.
4. Install the resulting debug APK on a real supported ARM64 Android device and
   verify both JNI-backed classification and fallback behavior.
5. Review crash behavior, native dependency notices, APK-size impact, and ABI
   coverage.
6. Verify the new navigation guard on an actual supported Android device,
   including HTTPS redirects, internationalized domains, the internal home
   page, and allowed external-link confirmation.
7. Its output is not a malware or safety verdict; do not label a URL safe solely
   because it classifies as HTTPS.

Do not merge this branch into `main` automatically. This pilot needs successful
CI and device testing plus explicit review before promotion.
