# Hari Browser review audit — 24 October 2026

## Scope and safety status

The uploaded review describes commit `71ac678`. At the time this branch was created, current `main` was `614ef6e6b2663c1b0298aa1f875b571bc4902e0d` (version 2.4.0 / versionCode 8), including merged PRs #47 and #48. PR #47 implemented scan history/tracker imports and related bounded controls; PR #48 implemented per-site page-script fingerprint exceptions. This document tracks the review recommendations against source; branch-only changes remain unverified until CI passes and the PR is reviewed.

The previously reported Android device-protection warning remains an unresolved release blocker. The release workflow checks the packaged DEX for the EICAR test marker and the post-merge release build passed, but this does not prove the device warning is a false positive. Build a fresh artifact, verify its signature/hash, scan independently, and test on a spare device. Never ignore or bypass the warning.

## Complete item-by-item audit

| Item from review | Current status | Action |
|---|---|---|
| Deny-by-default WebView, HTTPS-only navigation, TLS cancellation, Safe Browsing, encrypted store, private-process isolation, no general JS bridge, CI hygiene and India-focused home page | Present per the source review; manifest/source inspection supports the HTTPS/cleartext posture. Runtime behavior still needs device tests. | Preserve; add lifecycle/navigation behavior tests. |
| Device-protection warning and stable signed APK | Open; no conclusive runtime diagnosis or verified stable signed artifact. | P0: investigate fresh signed APK with independent scans and device testing. |
| Split `BrowserActivity.java` into controllers | Missing; source is about 3,700 lines. | Refactor incrementally after behavior tests. |
| Move `HomePage.java` into HTML/CSS/JS assets | Missing; source is about 1,250 lines. | Extract while preserving synthetic-origin action checks. |
| Move SecureStore/JSON work off UI thread | Partially missing; calls remain synchronous. | Use a lifecycle-safe background executor and test ordering/errors. |
| Importable ad/tracker host lists | Implemented in `main` via PR #47; CI passed. Hosts-format importer has bounded parser and custom matcher; it does not interpret full Adblock syntax. | Validate performance and false positives on device; keep built-in rules. |
| Readability-based article extraction | Missing; current reader mode injects CSS into the source document. | Add licensed and tested article extraction. |
| Persist tabs after process death | Missing; current pending URL restore supports theme recreation only. | Add bounded encrypted standard-mode tab restore; never persist private tabs into normal storage. |
| External handling for non-HTTPS schemes | Missing; schemes are blocked. | Consider a strict allowlist and confirmation for safe schemes; never blindly launch arbitrary intent/file/custom URLs. |
| Per-site fingerprint shield toggle | Implemented in `main` via PR #48; automated CI passed. Adds exact-host page-script exemption, explicit privacy confirmation, max 128 entries and status display; reduced network User-Agent and other protections remain active. Subdomains are separate. | Complete manual Android WebView compatibility checks; this is not a guarantee of anonymity. |
| Configurable offline-reading cap | Feature branch; CI pending. Adds settings choices for 4/8/12 pages and 8,000/24,000/32,000 characters per page, with hard ceilings of 12 pages and 32,000 characters. | Validate capture/display behavior and ensure shrinking limits does not corrupt unrelated saved pages. |
| In-app download scan history | Implemented in `main` via PR #47; automated CI passed. Bounded, sanitized last-20 scan-result list added and private-mode history is cleared at session exit. | Manually verify private-profile cleanup and result presentation; never label BASIC_CHECKS_ONLY as clean. |
| Expose additional search engines | Feature branch; CI pending. Adds Ecosia, Mojeek and Qwant to the picker, centralizes URL mapping and tests all selectable endpoints/query escaping. UI explains that strict SafeSearch is not forcibly enforceable for every provider. | Confirm tests pass and manually verify search results, URL interception and Teen Protection behavior. |
| Bookmark folders/reordering | Missing; flat list. | Add a backward-compatible data schema and tests. |
| Complete localization using Android resources | Partial; `BrowserUiText` translates some runtime strings, while many dialogs/toasts remain English. | Migrate gradually, checking Malayalam and other supported locales/fallbacks. |
| Instrumented behavioral tests | Partial; current suite is JVM tests. | Add Android tests for lifecycle, URL interception, settings and private cleanup. |
| Bound session approval cache | Implemented in `main` via PR #47; automated CI passed. Synchronized cache is bounded at 256 URLs. | Keep bounded and test eviction. |
| AI model selection visibility | Partial: named model list and discovery fallback exist; UI does not clearly identify the model that answered. | Show the resolved model in response details and test fallback. |
| Version 8 changelog | Added in `main` via PR #47. | Keep aligned with shipped code. |
| ZXing third-party attribution | `NOTICE` added in `main` via PR #47 for ZXing Core 3.5.4 / Apache-2.0. | Recheck whenever dependencies change. |
| Renderer crash recovery | Implemented in `main` via PR #47; automated CI passed. Restores the prior HTTPS URL rather than always returning home. | Test recovery and safety prompts on device. |

## Implemented on `main` in tested PRs

- PR #47: bounded download scan history (20 items), sanitized report fields, timestamp and valid SHA-256 display; hosts-format tracker-list import/removal (max 512 KiB, 20,000 lines, 10,000 unique hosts); session approval cache (256 URLs); HTTPS renderer recovery; focused tests; version 8 changelog and ZXing notice.
- PR #48: bounded (128-host) per-site fingerprint page-script exceptions with explicit privacy confirmation, per-site status reporting, private-session cleanup and focused policy/script-wrapper tests.

## Additional feature branch work

- `feature/offline-reading-limits` is adding configurable, bounded offline page/text limits and tests. This work is not considered done until its CI passes and the PR is reviewed.

## Next order

1. Resolve the device-protection alert with a fresh, independently scanned, consistently signed APK.
2. Add behavioral Android tests.
3. Make storage I/O asynchronous and persist standard-mode tabs securely.
4. Implement article extraction and verify the merged per-site fingerprint control on real WebView versions.
5. Complete localization and search-engine options.
6. Refactor large classes, then improve bookmarks and offline-reading settings.

No item is considered complete merely because a dashboard or document names it.
