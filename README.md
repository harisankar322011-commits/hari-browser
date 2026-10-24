# Hari Browser

**A privacy-focused Android browser with Indian-friendly tools and optional safety features.**

Hari Browser is an open-source Android browser built on **Android System WebView** for private family use. It combines everyday browsing tools with privacy-conscious defaults, optional Teen Protection, local URL checks, file-inspection tools, and an optional Hari AI assistant. This project is not being prepared for Google Play publishing; family sideloading still requires a verified, trusted APK.

> [!IMPORTANT]
> **Release caution:** A previously tested Hari Browser 2.3.0 APK was reported as triggering an Android device-protection warning: “malware detected.” The cause has not yet been established. This is neither proof that the APK is malicious nor evidence that the warning is a false positive. Treat that artifact as untrusted until it has been investigated; do not rely on a build or store check alone as proof that an APK is malware-free.

## At a glance

| Item | Details |
| --- | --- |
| Platform | Android |
| Application ID | `com.haricalcx.haribrowser` |
| Current source version | 2.4.0 (version code 8) |
| Minimum Android version | Android 9 (API 28) |
| Target / compile SDK | API 36 |
| Runtime | Android System WebView |
| License | MIT |

## Features

### Local cryptography tool

- Encrypt and decrypt text and user-selected files locally using Web Crypto: AES-256-GCM with PBKDF2-HMAC-SHA-256 (310,000 iterations in the current format).
- Export encrypted output through Android's system save-location picker; file input is limited to 8 MiB and encrypted containers to 12 MiB to bound memory use.
- The tool can encrypt files that the user has already exported from bookmarks or history. It does **not** automatically encrypt HARI Browser's live databases, saved passwords, or browser traffic.
- The cryptography screen uses a dedicated WebViewAssetLoader origin, blocks non-local navigation/resources, and exposes only a bounded save-file bridge to the bundled page.

### Browsing and everyday tools

- DuckDuckGo is the default search provider; Google, Bing, Brave Search, and Startpage are also available.
- Address bar accepts a web address or search query. Optional voice search asks Android's selected speech-recognition service for text in the selected dashboard language; audio/transcript handling follows that service's own privacy policy.
- Tabs, duplicate tabs, recently closed tabs, downloads, in-page search, desktop mode, reader mode, adjustable text size, page sharing, and full-screen web video. Normal-mode tabs are saved in the encrypted local store and restored after process death; private tabs are never saved in the normal session snapshot, and clear-data-on-exit removes it. The proposed reader-extraction update builds a bounded, text-only view locally using page text and inserts it as text rather than HTML; it is heuristic, may include clutter or miss article content, and still needs Android WebView testing.
- Optional **Read later** saves explicitly selected HTTPS pages as encrypted, text-only snapshots. The defaults are eight pages and 24,000 characters per page; Settings can configure 4/8/12 pages and 8,000/24,000/32,000 characters, with hard ceilings of 12 pages and 32,000 characters per page. It does not save images, live links, scripts, cookies, login sessions, or the full page layout; snapshots may be incomplete or out of date. Saving is disabled in Private mode, and saved pages can be removed individually or cleared.
- Bookmark reordering and optional folders (legacy bookmarks remain “Unfiled”), bookmark import/export, QR-code generation, printing to PDF, and a directory of curated destinations covering education and Indian public services. Folder labels and controls are translated for supported dashboard languages.
- A home dashboard with quick actions, personal HTTPS shortcuts, offline wallpapers, themed colours, and local tools such as a calculator, unit converter, notes, password generator, JSON formatter, and text tools.

### Optional external userscripts

Hari Browser includes a small, optional userscript manager for scripts written for page-context execution (similar to the basic `@grant none` subset used by Tampermonkey).

- Open **Settings → External userscripts** to import a `.js`/`.user.js` file or download a script as text from an HTTPS URL. The download is never executed automatically.
- New imports are **disabled by default**. Review each script's listed site origins and explicitly approve it before enabling.
- Supported metadata includes `@name`, `@description`, HTTPS `@match`/`@include`/`@exclude`, `@run-at` (`document-start`, `document-end`, or `document-idle`), and `@grant none`.
- Privileged `GM_*` APIs, `@require`, `@resource`, `@connect`, global all-sites patterns, automatic updates, and automatic script signing are not supported. External scripts must use explicit HTTPS domains; WebView must support document-start script injection.
- Scripts run inside the matching website's JavaScript context. They can read or alter that site's page content and same-origin page storage, so only install scripts from sources you trust. Hari Browser does not expose a native Android bridge to userscripts.
- Scripts are stored in Hari Browser's encrypted local store and are **never run in Private mode**. Script code is not certified malware-free; re-import updates manually and review changes before enabling.

Userscripts can break sites or expose page information. They are not browser extensions with isolated privileges and do not make untrusted scripts safe.

### Privacy-conscious defaults

- HTTP navigation is never opened in cleartext. When a user follows an HTTP link, Hari Browser can offer to try the same address over HTTPS; the HTTPS version may still fail and receives the normal site-safety preflight. Cleartext traffic remains disabled.
- An HTTPS upgrade is not a safety certification. Review the destination address and any site-safety warning before entering credentials, OTPs, payment details, or identity information.
- Android WebView Safe Browsing is enabled, certificate errors are not ignored, and mixed content is restricted.
- Third-party cookies, webpage access to local files/content, automatic pop-ups, and geolocation are restricted or disabled.
- Local ad/tracker host blocking offers best-effort protection; it is not a complete filter list.

- The **Maximum anti-fingerprinting shield** runs before page scripts when the installed Android System WebView supports document-start injection. It reduces User-Agent detail, hides selected high-entropy JavaScript properties (including User-Agent Client Hints where exposed), normalizes selected navigator values, rounds screen detail and device-pixel ratio, hides non-standard heap reporting, quantizes `performance.now()` to milliseconds, masks the WebGL debug renderer, and perturbs selected canvas/WebGL/offline-audio readbacks. The user's selected language remains available for localization. These measures are best-effort and may affect canvas-heavy apps, graphics tests, image tools, or high-precision timing. They cannot conceal the IP address, remove every request-header or engine-level signal, stop all first-party identifiers, or guarantee anonymity; worker/service-worker and other engine-level fingerprinting may remain.
- Optional **Lite mode** blocks website images and the locally listed tracker hosts to reduce page requests on metered or slow connections. It does not measure exact bytes saved, block every third-party resource, or guarantee lower data use on every site; disable it when a site needs images.
- Private browsing uses a separate WebView process/profile on supported Android versions, hides normal-profile bookmarks, and uses separate browser preferences. Common site data is cleared on exit on a best-effort basis.
- Bookmarks and optional browsing history are encrypted using an Android Keystore-backed key.
- No account is required for normal browsing. The app does not include advertising or analytics SDKs.

### Site-safety checks

Hari Browser can review a destination before opening it. On-device URL heuristics flag selected suspicious patterns, including embedded credentials, punycode hostnames, IP-literal hosts, unusual HTTPS ports, long query strings, and some combinations associated with Indian banking or government-service impersonation scams. A separate non-blocking reminder appears when the URL contains login, OTP, KYC, Aadhaar, identity, or verification terms. It inspects the URL only—not page content—and prompts the user to verify the hostname; it is not a verdict that the site is malicious or trustworthy.

Optional URLhaus reputation checks are available after explicit user confirmation, or through a separate opt-in for automatic checks. URLs are not sent for reputation checks from private browsing. URL-based heuristics and reputation services can miss threats, report false positives, or be unavailable. A warning-free result does **not** certify a website as safe.

### Hari Antivirus and download checks

Hari Browser includes file-inspection features for files downloaded through the browser and files selected by the user. Depending on the scan path, checks include SHA-256 calculation, file-format mismatches, executable or active file types, bounded archive inspection, APK metadata and signer-certificate details, and a small multi-pattern rule engine. The standard antivirus test marker was removed from production scanner bytecode as a precaution after a device-protection warning; Samsung has not confirmed the warning's cause, so this change alone does not establish that the alert is resolved. This does not make the scanner a full antivirus engine.

Optional MalwareBazaar hash reputation can send a file's SHA-256 hash to the service when the user configures an Auth-Key. The file contents and filename are not uploaded for that lookup. Optional URLhaus checks share only the URL being checked. These online checks are off until configured or explicitly enabled, and private browsing does not send hashes or URLs for reputation checks.

**These tools are an initial inspection layer, not a complete antivirus product.** The bundled rules are limited and do not receive an automatic, comprehensive malware-signature database update. Some formats or nested content may not be fully inspected, and rule matches can be false positives. Hari Browser cannot scan other apps' private data or provide device-wide protection. Keep Google Play Protect and Android security updates enabled, and do not open suspicious files.

### Optional Teen Protection

Teen Protection can be enabled in Settings and protected by a parent PIN. It uses local phrase checks, selected domain rules, and strict-search preferences for supported search providers. For unrecognized providers, it also checks query parameters on URL paths that clearly identify search-result pages; it does not inspect page contents or all search forms. It aims to block selected explicit adult-content, gambling, graphic-violence, self-harm-instruction, dangerous weapon/explosive-instruction, and illegal-drug purchase/manufacture searches while allowing prevention, educational, and help-seeking searches where possible.

Teen Protection is **not foolproof**. Startpage's filtering is controlled by Startpage itself, so Hari Browser does not enforce strict mode there. Search providers behave differently; altered wording, other languages, content inside pages, images, videos, other apps, or another browser may bypass it. It can also block legitimate searches. Anyone with sufficient access to the device may bypass the app-level PIN by clearing app data or uninstalling the app. For device-wide supervision, use Android parental controls or Google Family Link.

### Optional Hari AI

Hari AI is available from the home screen when configured. Add an API key in **Settings → Hari AI · API settings**, then use the connection test before chatting. The key is encrypted on the device using Android Keystore-backed storage, and chat context is kept in memory rather than saved as a transcript. **When you send a prompt, it is transmitted to the AI service configured by you** and requires internet access and a valid API key. Review that provider's privacy terms before use.

Hari AI is designed to support India-focused responses, including ₹/INR, lakh/crore conventions, IST, and supported Indian languages. It first tries a small set of preferred Flash models and, only if all are unavailable, asks the Gemini API for models available to the supplied key and retries eligible text-generation Flash models. It is optional and is not required for normal browsing.

### Languages and Indian-friendly features

The home dashboard supports 14 language options: English (India), Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi, Urdu, Spanish, French, and Arabic. The address bar, core navigation labels, Settings menu, voice-search prompts, and family-safety guide title use the selected language where translations are available. Some confirmation dialogs and all website content may still be in English. Voice recognition is handled by Android's selected speech service and follows that service's privacy policy. The start page also links to related Hari projects and the [@inspiring_creatives_minds Instagram account](https://www.instagram.com/inspiring_creatives_minds/).

## Security and privacy limits

Hari Browser is intended for family use and reduces some common risks, but it does not guarantee anonymity, prevent every tracking technique, or certify the safety of a website or file. Its local family-safety guide explains safe browsing habits, the limits of Teen Protection and file checks, and how external AI/voice services may process submitted content.

- Websites and search providers still receive the requests needed to deliver content. External services receive data whenever you choose to use them—for example, when you use Hari AI, translate a page with Google Translate, or opt in to reputation checks.
- Private mode helps separate browser data, but cannot erase downloads you explicitly save, screenshots, network/provider logs, or every trace outside the app's control. Exit cleanup is best-effort.
- JavaScript is enabled by default for compatibility and can be disabled in Settings. Blocking HTTP may prevent older sites from loading.
- URL checks, Teen Protection, and file scanning can miss threats or generate false positives. None should be treated as a security certification.
- A previously tested APK has an unresolved Android device-protection alert, as noted above. Investigate that alert before distributing or recommending the affected artifact.

## Get the app

Use only builds linked from the official [Hari Browser repository](https://github.com/harisankar322011-commits/hari-browser).

The repository's release workflow can produce a build artifact for family testing/sideloading. This is not a Google Play publishing workflow. Because the previously tested APK triggered a device-protection warning, do not install it or assume a newly built APK is safe until the exact artifact has been investigated and verified.

1. Open the repository's **Actions** tab.
2. Open the latest successful **Build Hari Browser Release APK** workflow run on `main` after the 2.4.0 change has merged.
3. Download the `hari-browser-2.4.0-release-apk` artifact and read the artifact's notes.
4. Check the APK filename and signing status before installation. An APK named `*-unsigned.apk` is not ready to install.
5. Do not install or redistribute the previously flagged 2.3.0 APK until the Android device-protection alert has been investigated.

For releases intended to be updated in place, maintainers must sign every version with the same private release key. CI must never generate a disposable key; without the stable signing secrets it should publish only an unsigned validation artifact that cannot be installed. Never commit a keystore or signing passwords to source control.

A successful build, CI check, or third-party review does not by itself prove an APK is malware-free. Verify the release artifact, signature, and source before installing it.

## Build from source

### Requirements

- JDK 17
- Android SDK Platform 36
- Android Gradle Plugin 9.4.1 (declared by the project)

The checked-in Gradle wrapper selects Gradle 9.6.0 and verifies the distribution checksum, so a separately installed Gradle is not required.

### Commands

```bash
git clone https://github.com/harisankar322011-commits/hari-browser.git
cd hari-browser

# Compile a debug APK
./gradlew :app:assembleDebug

# Run JVM unit tests
./gradlew :app:testDebugUnitTest

# Run release lint checks
./gradlew :app:lintRelease
```

Gradle writes APKs under `app/build/outputs/apk/`. A locally built APK should not be assumed to be signed for installation or for future updates. The family-use install candidate must be signed with the same stable, privately stored key on every update; never commit a keystore or signing password to source control. Do not install the previously flagged 2.3.0 APK while its device-protection warning remains unresolved.

The repository's GitHub Actions workflow also runs release-build checks, Android lint, Teen Protection unit tests, and source-level security regression checks. Passing these checks improves quality but cannot establish that the app is vulnerability-free.


## Contributing and issue reports

Bug reports and improvement suggestions are welcome through [GitHub Issues](https://github.com/harisankar322011-commits/hari-browser/issues). When reporting a problem, include the app version, Android version, device model, steps to reproduce, and relevant logs with personal information removed. Never include API keys, passwords, or signing credentials in an issue.

## License and third-party components

Hari Browser source code is available under the [MIT License](LICENSE). The ZXing Core dependency used for QR-code functionality is licensed separately under Apache License 2.0; third-party components remain subject to their own licenses.
