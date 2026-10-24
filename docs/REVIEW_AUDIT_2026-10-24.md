# Hari Browser review audit — 24 October 2026

## Scope and safety status

The uploaded review describes commit `71ac678`. Current `main` is `63843b58a0aefc87b0aa826d80ce18dfbab798d9` (version 2.4.0 / versionCode 8). This audit compares its recommendations with the current source. The improvements listed as “feature branch” are proposed on `feature/review-audit-download-history` and are **not complete until CI passes and the PR is reviewed**.

The previously reported Android device-protection warning remains an unresolved release blocker. The release workflow checks the packaged DEX for the EICAR test marker and the post-merge release build passed, but this does not prove the device warning is a false positive. Build a fresh artifact, verify its signature/hash, scan independently, and test on a spare device. Never ignore or bypass the warning.

## Complete item-by-item audit

| Item from review | Current status | Action |
|---|---|---|
| Deny-by-default WebView, HTTPS-only navigation, TLS cancellation, Safe Browsing, encrypted store, private-process isolation, no general JS bridge, CI hygiene and India-focused home page | Present per the source review; manifest/source inspection supports the HTTPS/cleartext posture. Runtime behavior still needs device tests. | Preserve; add lifecycle/navigation behavior tests. |
| Device-protection warning and stable signed APK | Open; no conclusive runtime diagnosis or verified stable signed artifact. | P0: investigate fresh signed APK with independent scans and device testing. |
| Split `BrowserActivity.java` into controllers | Missing; source is about 3,700 lines. | Refactor incrementally after behavior tests. |
| Move `HomePage.java` into HTML/CSS/JS assets | Missing; source is about 1,250 lines. | Extract while preserving synthetic-origin action checks. |
| Move SecureStore/JSON work off UI thread | Partially missing; calls remain synchronous. | Use a lifecycle-safe background executor and test ordering/errors. |
| Importable ad/tracker host lists | Feature branch: hosts-format importer, bounded parser and custom matcher added; CI pending. Does not interpret full Adblock syntax. | Verify parsing/performance and false positives; keep built-in rules. |
| Readability-based article extraction | Missing; current reader mode injects CSS into the source document. | Add licensed and tested article extraction. |
| Persist tabs after process death | Missing; current pending URL restore supports theme recreation only. | Add bounded encrypted standard-mode tab restore; never persist private tabs into normal storage. |
| External handling for non-HTTPS schemes | Missing; schemes are blocked. | Consider a strict allowlist and confirmation for safe schemes; never blindly launch arbitrary intent/file/custom URLs. |
| Per-site fingerprint shield toggle | Missing; current protection is global/best-effort. | Add site-specific override and visible state, retaining global safeguards. |
| Configurable offline-reading cap | Missing; fixed at 8 pages / 24,000 characters. | Add bounded preferences and hard maximums. |
| In-app download scan history | Feature branch: bounded, sanitized last-20 result list and UI added; CI pending. | Verify private-profile separation and presentation; never label BASIC_CHECKS_ONLY as clean. |
| Expose additional search engines | Partially missing; protection recognizes more providers than the visible picker exposes. | Test provider URL/SafeSearch behavior before exposing choices. |
| Bookmark folders/reordering | Missing; flat list. | Add a backward-compatible data schema and tests. |
| Complete localization using Android resources | Partial; `BrowserUiText` translates some runtime strings, while many dialogs/toasts remain English. | Migrate gradually, checking Malayalam and other supported locales/fallbacks. |
| Instrumented behavioral tests | Partial; current suite is JVM tests. | Add Android tests for lifecycle, URL interception, settings and private cleanup. |
| Bound session approval cache | Feature branch: synchronized 256-entry cache with eviction tests added; CI pending. | Keep bounded and test eviction. |
| AI model selection visibility | Partial: named model list and discovery fallback exist; UI does not clearly identify the model that answered. | Show the resolved model in response details and test fallback. |
| Version 8 changelog | Added on feature branch. | Keep aligned with shipped code. |
| ZXing third-party attribution | `NOTICE` added on feature branch for ZXing Core 3.5.4 / Apache-2.0. | Recheck whenever dependencies change. |
| Renderer crash recovery | Feature branch restores the prior HTTPS URL rather than always returning home; CI pending. | Test recovery and safety prompts on device. |

## Work prepared on the feature branch

- Bounded download scan history (20 items), sanitized text fields, timestamp and valid SHA-256 display.
- Custom hosts-format tracker-list import/removal: maximum 512 KiB, 20,000 lines and 10,000 unique hosts. Import is normal-mode only; the built-in tracker list remains.
- Bounded session approvals (256 URLs) and recovery of the previous HTTPS page after renderer crash.
- Focused JVM tests for scan history, host-list parsing/matching and approval eviction.
- Version 8 changelog and third-party notice.

## Next order

1. Resolve the device-protection alert with a fresh, independently scanned, consistently signed APK.
2. Add behavioral Android tests.
3. Make storage I/O asynchronous and persist standard-mode tabs securely.
4. Implement article extraction and per-site fingerprint controls.
5. Complete localization and search-engine options.
6. Refactor large classes, then improve bookmarks and offline-reading settings.

No item is considered complete merely because a dashboard or document names it.
