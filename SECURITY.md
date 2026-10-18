# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature for this repository if it is enabled. Otherwise, open a minimal issue without publishing exploit details and ask for a private reporting channel. Do not include private browsing data, passwords, session cookies, or other personal information.

## Security design

- Network requests from web content are HTTPS-only; cleartext traffic is disabled in the Android manifest and network security configuration.
- WebView Safe Browsing is enabled. TLS certificate errors are always cancelled.
- Mixed content, local file access, content-provider access, geolocation, popup windows, and web camera/microphone permission requests are blocked or disabled.
- Third-party cookies are disabled by the browser.
- Bookmarks and opt-in history are encrypted using AES-GCM with a key stored in Android Keystore.
- Android backup and device-transfer backup are disabled/excluded.
- No app analytics or advertising SDK is included. The content blocker is local and does not download remote blocklists.
- GitHub Actions build jobs use read-only repository permissions, pin third-party actions to full commit SHAs, avoid persisting checkout credentials, run source-level security regression checks, run CodeQL and dependency review, and upload a debug APK for testing.
- CI checks the debug APK signature and ZIP integrity, ensures the release variant is not debuggable, validates APK package IDs and the release permission manifest, and publishes SHA-256 checksums. These are build-integrity checks, not a malware certificate or independent security audit.
- Production signing keys and passwords must never be committed to this repository or exposed to build logs.

## Known limitations

This app uses the Android System WebView supplied by the device and is not a Chromium fork. Security fixes therefore depend on keeping Android System WebView and Android updated. The local tracker host list is incomplete and may miss tracking domains or block some legitimate resources. JavaScript is on by default for compatibility, and disabling it may improve safety on simple sites. The search provider and websites receive the requests required to answer searches and load content.

Private browsing runs with a separate WebView data-directory suffix on supported Android versions and clears common cookies/site storage/cache on exit. It does not read, save, import, remove, or export standard-profile bookmarks, and its browser settings use a separate preference file. Cleanup remains best-effort: it does not provide network anonymity and cannot erase files deliberately downloaded, screenshots, operating-system or network logs, or every artifact maintained by WebView. Do not use this project as a substitute for a professionally audited browser for high-risk activities.

The CI artifact is debug-signed and intended for testing/sideloading only; it is not a Play Store production release.
