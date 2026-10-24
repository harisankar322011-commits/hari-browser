# Hari Browser Review Audit — 24 October 2026

## Scope and evidence

The supplied review inspected commit `71ac678` (2.4.0 / versionCode 8). This ledger is reconciled against main commit `f2a61b11ba35c2fa0f811f30790784df6db5bfb6`, including merged PRs #52, #55, #57, #58 and #60. PR #60 passed all six required checks and is merged; automated checks still do not replace Android-device verification.

The previously reported Android device-protection warning remains an **unresolved release blocker**. Passing source checks and CI builds does not prove the old APK was safe or the warning was a false positive. A freshly built APK still requires signature/hash verification, independent scanning and careful device testing. Never bypass the warning. The CI pipeline can produce an unsigned validation artifact if the stable release signer is not configured.

Static inspection of the CI validation artifact built from commit `f2a61b11ba35c2fa0f811f30790784df6db5bfb6` was also performed on 24 October 2026: workflow run `40103135233` produced `HariBrowser-2.4.0-unsigned.apk` (12,689,438 bytes; SHA-256 `4608683d3c68d9ff2a5967cdb438a9469773c3e8d5460e792195e076a62107af`). The APK archive passed ZIP integrity validation and the standard EICAR test marker was not found in its DEX. It has no APK signing certificate/signature entries and is explicitly unsigned; it is not installable as a release artifact and is not necessarily the same APK that triggered the user's Android warning. No independent antivirus or dynamic-device scan was performed, so this evidence does not clear the warning or prove the APK malware-free.

Comparison with the previously uploaded `HariBrowser-2.3.0(2).apk` found the EICAR marker in its `classes.dex` (APK SHA-256 `d44eb34d6a2f20b676ed88c9086f8d7c7cd0f4f06c4d50048f1d0c0273ffe683`). The source at commit `923a8eb1343d0b6fc51c06c4411bb00b40baa560` contained a production `EICAR_TEST_SIGNATURE` constant and scanner match logic; commit `aa5d1c51a969ff5aff9657d78348cfc5ef00aa90` explicitly removed that test marker and added CI checks that reject it in DEX files. The separately uploaded `HariBrowser-2.3.0.apk` did not contain the marker (SHA-256 `b6ce731affa845555280f98b7a46f0435a08b575589f471d1259f30110ca49d4`). This makes the bundled test marker a strong, actionable explanation for why the `2.3.0(2)` artifact may have triggered Android protection, but it does not prove that it was the only cause or that the device warning was exclusively caused by this string.

This is a source audit and prioritized implementation ledger, not a claim of full runtime verification. All user-visible security features require device testing as well as automated checks.

## Full review-item status

| Review recommendation | Status on current main | Remaining action |
|---|---|---|
| Resolve Android “malware detected” warning; build/sign/scan fresh APK | **OPEN — release blocker.** The source smoke checks prohibit the EICAR test marker in production scanner code. A previous artifact reportedly triggered device protection; no conclusive root-cause analysis or successful independent scan of a new APK is recorded here. | Build from reviewed main, inspect SHA-256/signature, use reputable independent scan services and a spare device. Do not dismiss the warning without evidence. Confirm stable signing key before distributing an install/update build. |
| Deny-by-default WebView, HTTPS-only navigation, TLS cancellation, Safe Browsing, private isolation, encrypted store, no general JS bridge | **Present in source per the review and static tests.** These are important existing guarantees. | Preserve these guarantees; add behavioral device tests for navigation, process lifecycle and private cleanup. |
| Split \`BrowserActivity.java\` into controllers | **NOT IMPLEMENTED.** The large activity still owns lifecycle, tabs, navigation, settings, downloads, AI and userscripts. | Extract one controller at a time after behavioral coverage; avoid risky rewrites. |
| Move \`HomePage.java\` into local HTML/CSS/JS assets | **NOT IMPLEMENTED.** Page content is still assembled by Java. | Extract into assets while preserving the synthetic origin and native action checks. |
| Move encrypted storage and JSON work off UI thread | **PARTIAL — PRs #57 and #60 merged.** History and tab-session writes use a serialized queue; asynchronous `SecureStore.getAsync/updateAsync` now supports background reads and serialized bookmark read/modify/write flows. Other paths still need migration. | CI and device-test the new callbacks and error paths; migrate remaining offline-reading, userscript, AI-key, import/export and startup reads in smaller batches. |
| Importable ad/tracker host lists | **IMPLEMENTED — PR #47, CI passed.** Bounded hosts-format parser/import/remove control, DNS suffix matching and built-in list retained. It does not interpret full Adblock/EasyList syntax. | Device-test performance and false positives; consider a reviewed filter subset separately. |
| Readability-based article extraction | **PARTIAL — PR #60 merged.** Local heuristic selects article/main-like containers and creates a bounded text-only reader DOM without copying page HTML or loading a third-party script. This is not Mozilla Readability and can fail on site-specific layouts. | Run JVM/CI tests, then test hostile HTML, long pages, Malayalam/Indic text, navigation and fallback behavior on actual Android System WebView versions. Reassess a reviewed Readability library only if the heuristic is insufficient. |
| Persist tabs after process death | **IMPLEMENTED — PR #55 merged.** Bounded HTTPS-only session codec and integration use the encrypted `SecureStore`; private tabs are excluded and clear-data-on-exit removes the snapshot. | Manually verify cold-start restore, external-link launch, private-session isolation and clear-data-on-exit on an Android device. |
| Open supported non-HTTPS schemes externally | **IMPLEMENTED — PR #52 merged.** Strict validation and explicit confirmation for `mailto:` and `tel:`; arbitrary schemes remain blocked. CI passed for debug/release builds, unit tests/lint, CodeQL, secret scanning and dependency review. | Device-test mail/dialer handlers and missing-app behavior. |
| Per-site anti-fingerprinting control | **IMPLEMENTED — PR #48, CI passed.** Exact-host exception, explicit confirmation, 128-host cap, visible status and private-session cleanup. The page-script reductions are disabled for that hostname only; reduced network User-Agent and other browser protections remain. | Check compatibility behavior across supported Android System WebView versions; this is not a promise of anonymity. |
| Configurable offline-reading page/text cap | **IMPLEMENTED — PR #49, CI passed.** User choices 4/8/12 pages and 8k/24k/32k characters; hard ceilings 12 pages / 32k characters and existing defaults remain 8 / 24k. | Manually verify capture/display at each setting and ensure changing limits does not unexpectedly lose unrelated saved pages. |
| In-app download scan history | **IMPLEMENTED — PR #47, CI passed.** Bounded, sanitized history of last 20 scan reports, local details and clear action; private-mode report history is cleared on session exit. | Verify display and private cleanup on device; limited heuristics must never be presented as proof that a file is malware-free. |
| Expose additional search engines | **IMPLEMENTED — PR #51, CI passed.** Ecosia, Mojeek and Qwant added alongside existing providers; centralized HTTPS URL mapping and query encoding; local query filtering tests for new providers. UI discloses that forced SafeSearch is provider-dependent. | Manually confirm each provider's URLs/results. Teen Protection is still incomplete and some providers need their own SafeSearch settings. |
| Bookmark folders/reorder | **IMPLEMENTED IN SOURCE — PRs #58 and #60 merged.** Bookmark reorder controls plus optional folder metadata, folder filtering and move/create UI are present; legacy records without folder metadata remain `Unfiled`. Folder names are normalized/bounded; only valid HTTPS bookmarks are shown in folder views. Device verification remains outstanding. | Await PR #60 CI, exercise create/move/unfiled/filter flows and malformed JSON on device, and consider import/export round trips. |
| Complete localization through Android resources | **PARTIAL.** Home page and \`BrowserUiText\` cover many languages, but runtime dialogs, toasts and security labels still contain English literals. | Migrate in batches to Android string resources; verify Malayalam and fallback coverage. |
| Instrumented/Robolectric behavior tests | **PARTIAL.** JVM unit tests and static smoke tests exist; static checks cannot prove full runtime behavior. | Add Android behavioral tests for navigation interception, WebView lifecycle, settings toggles, downloads, private cleanup and tab restore. |
| Bound session approval cache | **IMPLEMENTED — PR #47, CI passed.** Synchronized insertion-ordered cache capped at 256 URLs with eviction tests. | Keep the bound and tests. |
| Display selected Hari AI model | **IMPLEMENTED IN THIS PR.** HariAiClient returns both response text and the actual model ID; the chat status shows `Ready · Model: <id>` without inserting model metadata into the conversation sent back to the API. Focused JVM tests cover attribution data. | Verify fallback-model attribution with a configured API key only after owner approval; do not send live API requests automatically. |
| Version 8 changelog | **IMPLEMENTED — PR #47.** Changelog added. | Keep it aligned with actual release contents. |
| Third-party ZXing notice | **IMPLEMENTED — PR #47.** \`NOTICE\` records ZXing Core 3.5.4 and Apache-2.0. | Recheck licenses on dependency updates and consider a complete transitive license inventory. |
| Restore prior HTTPS URL after renderer crash | **IMPLEMENTED — PR #47, CI passed.** Recreates the WebView and restores prior HTTPS URL rather than always returning home. | Exercise renderer recovery and normal safety prompts on-device. |

## Merged changes

- **PR #57 — serialized background storage:** moved automatic history read/modify/write and tab-session save/clear to the process-wide single-thread storage queue; focused FIFO/off-caller tests and CI passed before merge.
- **PR #58 — bookmark reordering:** added Move Up/Move Down controls and metadata-preserving HTTPS-only reorder operations with boundary and malformed-data tests; CI passed before merge.

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

## Merged changes — encrypted tab-session restore (PR #55)

- Stores a bounded snapshot of up to 20 normal-mode tabs through the encrypted Android Keystore-backed `SecureStore`.
- Restores only safe HTTPS URLs (and home-tab placeholders); restored URLs still flow through normal navigation/site-safety checks.
- Never writes private-mode tabs to normal-mode storage; the clear-data-on-exit setting clears the snapshot.
- Adds focused JVM tests for URL validation, malformed/oversized data, tab bounds and selected-index clamping.
- CI merge is confirmed. Manual device verification of cold-start restoration and cleanup remains outstanding; this change does not resolve the separate device-protection warning.

## Merged changes — PR #60 (reader extraction, bookmark folders and storage queue)

- `ReaderModeStyles` now attempts local, bounded text-only article extraction using candidate containers and `textContent`, avoiding `innerHTML` and remote assets. It is deliberately described as a heuristic, not a full Readability engine.
- `SecureStore` adds callback-based asynchronous reads and serialized read/modify/write support; bookmarks can be read, added, removed, reordered and moved between folders through the background queue. History and tab-session saves remain queued.
- `BookmarkFolders` adds backward-compatible optional `folder` metadata, an `Unfiled` default, a 40-character folder-name limit and up to 50 visible folder names. Folder actions are localized across 13 supported languages.
- All six required CI checks passed before merge: CodeQL, debug APK validation, Android APK builds, secret scanning, Java analysis and dependency review. The UI changes still need Android-device verification, and other storage paths remain synchronous. No security warning is cleared by these source changes.

## Recommended next implementation order

1. Resolve and document the Android device-protection warning using a newly built, scanned and consistently signed artifact.
2. Add behavioral Android tests around URL interception, WebView lifecycle, private cleanup and tabs.
3. Complete the remaining encrypted-store and JSON migrations in small, serialized, lifecycle-safe batches.
4. Validate the heuristic reader extraction on multiple real WebView versions and hostile/multilingual content; decide whether a reviewed Readability dependency is necessary.
5. Continue localization and validate bookmark folder create/move/filter plus import/export compatibility.
6. Split `BrowserActivity` and `HomePage` gradually.

No item is complete merely because it appears in a dashboard or documentation, and no APK should be described as release-ready until the old device-protection issue is investigated.