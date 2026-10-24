# Hari Browser Review Audit — 24 October 2026

## Scope and evidence

The supplied review inspected commit \`71ac678\` (2.4.0 / versionCode 8). The current source baseline for this implementation batch is main commit \`d208dbaa7379923d5a783b4137541a08160535b6\`, after the changes described below.

The previously reported Android device-protection warning remains an **unresolved release blocker**. Passing source checks and CI builds does not prove the old APK was safe or the warning was a false positive. A freshly built APK still requires signature/hash verification, independent scanning and careful device testing. Never bypass the warning. The CI pipeline can produce an unsigned validation artifact if the stable release signer is not configured.

This is a source audit and prioritized implementation ledger, not a claim of full runtime verification. All user-visible security features require device testing as well as automated checks.

## Full review-item status

| Review recommendation | Status on current main | Remaining action |
|---|---|---|
| Resolve Android “malware detected” warning; build/sign/scan fresh APK | **OPEN — release blocker.** The source smoke checks prohibit the EICAR test marker in production scanner code. A previous artifact reportedly triggered device protection; no conclusive root-cause analysis or successful independent scan of a new APK is recorded here. | Build from reviewed main, inspect SHA-256/signature, use reputable independent scan services and a spare device. Do not dismiss the warning without evidence. Confirm stable signing key before distributing an install/update build. |
| Deny-by-default WebView, HTTPS-only navigation, TLS cancellation, Safe Browsing, private isolation, encrypted store, no general JS bridge | **Present in source per the review and static tests.** These are important existing guarantees. | Preserve these guarantees; add behavioral device tests for navigation, process lifecycle and private cleanup. |
| Split \`BrowserActivity.java\` into controllers | **NOT IMPLEMENTED.** The large activity still owns lifecycle, tabs, navigation, settings, downloads, AI and userscripts. | Extract one controller at a time after behavioral coverage; avoid risky rewrites. |
| Move \`HomePage.java\` into local HTML/CSS/JS assets | **NOT IMPLEMENTED.** Page content is still assembled by Java. | Extract into assets while preserving the synthetic origin and native action checks. |
| Move encrypted storage and JSON work off UI thread | **PARTIAL / OPEN.** \`SecureStore\` uses AES-GCM/Keystore and fails closed, but callers still perform synchronous read/write work. | Add a lifecycle-safe executor, serialized writes and UI callbacks; test concurrency/error handling. |
| Importable ad/tracker host lists | **IMPLEMENTED — PR #47, CI passed.** Bounded hosts-format parser/import/remove control, DNS suffix matching and built-in list retained. It does not interpret full Adblock/EasyList syntax. | Device-test performance and false positives; consider a reviewed filter subset separately. |
| Readability-based article extraction | **NOT IMPLEMENTED.** Reader mode remains CSS restyling rather than article extraction. | Bundle a reviewed, licensed Readability implementation; test hostile HTML, multi-language pages and attribution. |
| Persist tabs after process death | **NOT IMPLEMENTED.** Existing restore path is for theme recreation, not durable cold-start restoration. | Save a bounded HTTPS-only standard-mode session in encrypted storage, restore only through normal safety checks, and never save private tabs in standard storage. |
| Open supported non-HTTPS schemes externally | **IMPLEMENTED — PR #52 merged.** Strict validation and explicit confirmation for `mailto:` and `tel:`; arbitrary schemes remain blocked. CI passed for debug/release builds, unit tests/lint, CodeQL, secret scanning and dependency review. | Device-test mail/dialer handlers and missing-app behavior. |
| Per-site anti-fingerprinting control | **IMPLEMENTED — PR #48, CI passed.** Exact-host exception, explicit confirmation, 128-host cap, visible status and private-session cleanup. The page-script reductions are disabled for that hostname only; reduced network User-Agent and other browser protections remain. | Check compatibility behavior across supported Android System WebView versions; this is not a promise of anonymity. |
| Configurable offline-reading page/text cap | **IMPLEMENTED — PR #49, CI passed.** User choices 4/8/12 pages and 8k/24k/32k characters; hard ceilings 12 pages / 32k characters and existing defaults remain 8 / 24k. | Manually verify capture/display at each setting and ensure changing limits does not unexpectedly lose unrelated saved pages. |
| In-app download scan history | **IMPLEMENTED — PR #47, CI passed.** Bounded, sanitized history of last 20 scan reports, local details and clear action; private-mode report history is cleared on session exit. | Verify display and private cleanup on device; limited heuristics must never be presented as proof that a file is malware-free. |
| Expose additional search engines | **IMPLEMENTED — PR #51, CI passed.** Ecosia, Mojeek and Qwant added alongside existing providers; centralized HTTPS URL mapping and query encoding; local query filtering tests for new providers. UI discloses that forced SafeSearch is provider-dependent. | Manually confirm each provider's URLs/results. Teen Protection is still incomplete and some providers need their own SafeSearch settings. |
| Bookmark folders/reorder | **NOT IMPLEMENTED.** Bookmarks remain a flat list. | Add a backward-compatible encrypted data schema and ordering/migration tests. |
| Complete localization through Android resources | **PARTIAL.** Home page and \`BrowserUiText\` cover many languages, but runtime dialogs, toasts and security labels still contain English literals. | Migrate in batches to Android string resources; verify Malayalam and fallback coverage. |
| Instrumented/Robolectric behavior tests | **PARTIAL.** JVM unit tests and static smoke tests exist; static checks cannot prove full runtime behavior. | Add Android behavioral tests for navigation interception, WebView lifecycle, settings toggles, downloads, private cleanup and tab restore. |
| Bound session approval cache | **IMPLEMENTED — PR #47, CI passed.** Synchronized insertion-ordered cache capped at 256 URLs with eviction tests. | Keep the bound and tests. |
| Display selected Hari AI model | **IMPLEMENTED IN THIS PR.** HariAiClient returns both response text and the actual model ID; the chat status shows `Ready · Model: <id>` without inserting model metadata into the conversation sent back to the API. Focused JVM tests cover attribution data. | Verify fallback-model attribution with a configured API key only after owner approval; do not send live API requests automatically. |
| Version 8 changelog | **IMPLEMENTED — PR #47.** Changelog added. | Keep it aligned with actual release contents. |
| Third-party ZXing notice | **IMPLEMENTED — PR #47.** \`NOTICE\` records ZXing Core 3.5.4 and Apache-2.0. | Recheck licenses on dependency updates and consider a complete transitive license inventory. |
| Restore prior HTTPS URL after renderer crash | **IMPLEMENTED — PR #47, CI passed.** Recreates the WebView and restores prior HTTPS URL rather than always returning home. | Exercise renderer recovery and normal safety prompts on-device. |

## Merged changes

- **PR #47 — tracker lists, download history and bounded state:** \`DownloadScanHistory\` (20 reports); hosts-format list import/removal with limits (512 KiB / 20,000 lines / 10,000 unique hosts); a 256-entry session approval cache; HTTPS renderer recovery; focused tests; version 8 changelog; ZXing notice. CI passed before merge.
- **PR #48 — per-site page-script fingerprint controls:** exact-host overrides with explicit consent; bounded 128-host policy; visible status; private-session cleanup; host-policy and script-wrapper tests. CI passed before merge.
- **PR #49 — offline reading limits:** configurable page/text caps with hard ceilings; capture/read/remove paths honor selected preferences; focused JVM tests. CI passed before merge.
- **PR #51 — additional search providers:** Ecosia, Mojeek and Qwant; central URL generator and query encoding; Teen Protection checks for new providers; honest SafeSearch limitation text; static smoke test updated to validate the centralized URL helper. CI passed before merge.

## Merged changes — safe external links (PR #52)

- `ExternalLinkPolicy` classifies normal web/search input, HTTPS, HTTP, validated mailto/tel links and blocked schemes.
- `mailto:` is reduced to a validated recipient only. Query/subject/body metadata is not forwarded, and encoded CR/LF is rejected to guard against header injection.
- `tel:` is reduced to a normalized number; malformed or decorated targets are rejected. Confirmation launches `ACTION_DIAL`, never `ACTION_CALL`, so the browser does not place a call.
- The user must explicitly confirm opening the email app or dialer. Missing handlers are handled.
- `file:`, `intent:`, `market:`, `javascript:`, `data:`, `content:` and other non-allowlisted schemes remain blocked.
- Focused unit tests and static smoke checks passed with debug/release builds, CodeQL, secret scanning and dependency review. Device testing of actual mail/dialer handlers remains outstanding.

## Recommended next implementation order

1. Resolve and document the Android device-protection warning using a newly built, scanned and consistently signed artifact.
2. Add behavioral Android tests around URL interception, WebView lifecycle, private cleanup and tabs.
3. Persist standard-mode tabs safely; then move encrypted-store calls off the UI thread.
4. Implement Readability-based reader extraction and verify per-site fingerprint controls on real WebView versions.
5. Complete localization and improve bookmarks.
6. Split `BrowserActivity` and `HomePage` gradually.

No item is complete merely because it appears in a dashboard or documentation, and no APK should be described as release-ready until the old device-protection issue is investigated.