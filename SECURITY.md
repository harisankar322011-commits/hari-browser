# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability reporting feature for this repository if it is enabled. Otherwise, open a minimal issue without publishing exploit details and ask for a private reporting channel. Do not include private browsing data, passwords, session cookies, or other personal information.

## Security design

- Network requests from web content are HTTPS-only; cleartext traffic is disabled in the Android manifest and network security configuration.
- New top-level destinations receive a local URL-structure preflight before loading. This is a heuristic warning, not a reputation verdict: it does not fetch or certify the site, inspect response headers/content, or detect all malicious URLs. Android WebView Safe Browsing and TLS certificate validation remain separate connection-time protections.
- Hari Antivirus offers user-selected and browser-download file checks: SHA-256, selected format mismatches, bounded ZIP-based archive inspection and limited APK package metadata review. The standard antivirus test marker is not included in production scanner bytecode because its presence contributed to a plausible explanation for a reported device-protection warning; Samsung has not confirmed that as the trigger. The scanner is not a full ClamAV/YARA-class malware engine and has no comprehensive offline malware-signature database or automatic signature updates.
- Optional MalwareBazaar reputation is off until the user configures an abuse.ch Auth-Key. The app sends only a SHA-256 hash for a user-initiated or tracked non-private download lookup, never file contents or filenames. Private-mode downloads do not make reputation requests. An unknown hash is not a clean verdict.
- URLhaus reputation is OFF by default. A manual check sends a user-pasted URL only after confirmation; optional automatic checks send normal-session navigation URLs before opening only after a separate explicit opt-in. The URL query cache stays in memory only, and private sessions never submit URLs. URLhaus primarily tracks malware-distribution URLs and is not a complete phishing/scam list. No-match or service-error results do not certify a site as safe.
- Quarantine copies remain in no-backup app-private storage and are integrity-checked. Removing the original is a best-effort SAF operation; if Android denies deletion, the UI warns that the original may remain. Android's app sandbox does not let a normal browser inspect or clean other apps' private files; device-wide scanning is not provided.
- WebView Safe Browsing is enabled. TLS certificate errors are always cancelled.
- Mixed content, local file access, content-provider access, geolocation, popup windows, and web camera/microphone permission requests are blocked or disabled.
- Third-party cookies are disabled by the browser.
- Bookmarks and opt-in history are encrypted using AES-GCM with a key stored in Android Keystore.
- Android backup and device-transfer backup are disabled/excluded.
- No app analytics or advertising SDK is included. The content blocker is local and does not download remote blocklists. Hash reputation is disabled unless configured; URL reputation sharing is explicitly opted in and disabled by default.
- GitHub Actions use read-only repository permissions and pin third-party actions to full commit SHAs. Source-level security regression checks, CodeQL, dependency review, and Gitleaks secret scanning run in CI; Gitleaks scans the full Git history on pushes and pull requests and runs weekly. The secret-scan workflow disables PR comments and finding-artifact uploads.
- CI checks APK ZIP integrity, ensures the release variant is not debuggable, validates APK package IDs and the release permission manifest, scans every production DEX file for the known antivirus test marker, and publishes a SHA-256 digest in the workflow summary. A release APK is signed only when all four protected `HARI_RELEASE_*` secrets provide a stable private signing key; otherwise CI publishes an explicitly named unsigned validation artifact that cannot be installed. CI must never generate and discard a release key. These are build-integrity checks, not a malware certificate or independent security audit.
- Production signing keys and passwords must never be committed to this repository or exposed to build logs.

## Known limitations

This app uses the Android System WebView supplied by the device and is not a Chromium fork. Security fixes therefore depend on keeping Android System WebView and Android updated. The local tracker host list is incomplete and may miss tracking domains or block some legitimate resources. JavaScript is on by default for compatibility, and disabling it may improve safety on simple sites. The search provider and websites receive the requests required to answer searches and load content.

Private browsing runs with a separate WebView data-directory suffix on supported Android versions and clears common cookies/site storage/cache on exit. It does not read, save, import, remove, or export standard-profile bookmarks, and its browser settings use a separate preference file. Cleanup remains best-effort: it does not provide network anonymity and cannot erase files deliberately downloaded, screenshots, operating-system or network logs, or every artifact maintained by WebView. Do not use this project as a substitute for a professionally audited browser for high-risk activities.

The CI artifact is debug-signed and intended for testing/sideloading only; it is not a Play Store production release.
