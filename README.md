# Hari Browser

**A privacy-focused Android browser with Indian-friendly tools and optional safety features.**

Hari Browser is an open-source browser built on **Android System WebView**. It combines everyday browsing tools with privacy-conscious defaults, optional Teen Protection, local URL checks, file-inspection tools, and an optional Hari AI assistant.

> [!IMPORTANT]
> **Release caution:** A previously tested Hari Browser 2.3.0 APK was reported as triggering an Android device-protection warning: “malware detected.” The cause has not yet been established. This is neither proof that the APK is malicious nor evidence that the warning is a false positive. Treat that artifact as untrusted until it has been investigated; do not rely on a build or store check alone as proof that an APK is malware-free.

## At a glance

| Item | Details |
| --- | --- |
| Platform | Android |
| Application ID | `com.haricalcx.haribrowser` |
| Current source version | 2.3.0 (version code 7) |
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
- Address bar accepts a web address or a search query.
- Tabs, duplicate tabs, recently closed tabs, downloads, in-page search, desktop mode, reader-style mode, adjustable text size, page sharing, and full-screen web video.
- QR-code generation, printing to PDF, bookmark import/export, and a directory of curated destinations covering education and Indian public services.
- A home dashboard with quick actions, personal HTTPS shortcuts, offline wallpapers, themed colours, and local tools such as a calculator, unit converter, notes, password generator, JSON formatter, and text tools.

### Privacy-conscious defaults

- HTTP navigation is rejected rather than silently downgraded; cleartext traffic is disabled.
- Android WebView Safe Browsing is enabled, certificate errors are not ignored, and mixed content is restricted.
- Third-party cookies, webpage access to local files/content, automatic pop-ups, and geolocation are restricted or disabled.
- Local ad/tracker host blocking offers best-effort protection; it is not a complete filter list.
- Optional **Lite mode** blocks website images and the locally listed tracker hosts to reduce page requests on metered or slow connections. It does not measure exact bytes saved, block every third-party resource, or guarantee lower data use on every site; disable it when a site needs images.
- Private browsing uses a separate WebView process/profile on supported Android versions, hides normal-profile bookmarks, and uses separate browser preferences. Common site data is cleared on exit on a best-effort basis.
- Bookmarks and optional browsing history are encrypted using an Android Keystore-backed key.
- No account is required for normal browsing. The app does not include advertising or analytics SDKs.

### Site-safety checks

Hari Browser can review a destination before opening it. On-device URL heuristics flag selected suspicious patterns, including embedded credentials, punycode hostnames, IP-literal hosts, unusual HTTPS ports, long query strings, and some combinations associated with Indian banking or government-service impersonation scams.

Optional URLhaus reputation checks are available after explicit user confirmation, or through a separate opt-in for automatic checks. URLs are not sent for reputation checks from private browsing. URL-based heuristics and reputation services can miss threats, report false positives, or be unavailable. A warning-free result does **not** certify a website as safe.

### Hari Antivirus and download checks

Hari Browser includes file-inspection features for files downloaded through the browser and files selected by the user. Depending on the scan path, checks include SHA-256 calculation, file-format mismatches, executable or active file types, bounded archive inspection, APK metadata and signer-certificate details, and a small multi-pattern rule engine. The standard antivirus test marker is intentionally kept out of production scanner bytecode to avoid triggering device-protection heuristics; this does not make the scanner a full antivirus engine.

Optional MalwareBazaar hash reputation can send a file's SHA-256 hash to the service when the user configures an Auth-Key. The file contents and filename are not uploaded for that lookup. Optional URLhaus checks share only the URL being checked. These online checks are off until configured or explicitly enabled, and private browsing does not send hashes or URLs for reputation checks.

**These tools are an initial inspection layer, not a complete antivirus product.** The bundled rules are limited and do not receive an automatic, comprehensive malware-signature database update. Some formats or nested content may not be fully inspected, and rule matches can be false positives. Hari Browser cannot scan other apps' private data or provide device-wide protection. Keep Google Play Protect and Android security updates enabled, and do not open suspicious files.

### Optional Teen Protection

Teen Protection can be enabled in Settings and protected by a parent PIN. It uses local phrase checks, selected domain rules, and strict-search preferences for supported search providers. For unrecognized providers, it also checks query parameters on URL paths that clearly identify search-result pages; it does not inspect page contents or all search forms. It aims to block selected explicit adult-content, gambling, graphic-violence, self-harm-instruction, dangerous weapon/explosive-instruction, and illegal-drug purchase/manufacture searches while allowing prevention, educational, and help-seeking searches where possible.

Teen Protection is **not foolproof**. Startpage's filtering is controlled by Startpage itself, so Hari Browser does not enforce strict mode there. Search providers behave differently; altered wording, other languages, content inside pages, images, videos, other apps, or another browser may bypass it. It can also block legitimate searches. Anyone with sufficient access to the device may bypass the app-level PIN by clearing app data or uninstalling the app. For device-wide supervision, use Android parental controls or Google Family Link.

### Optional Hari AI

Hari AI is available from the home screen when configured. Add an API key in **Settings → Hari AI · API settings**, then use the connection test before chatting. The key is encrypted on the device using Android Keystore-backed storage, and chat context is kept in memory rather than saved as a transcript. **When you send a prompt, it is transmitted to the AI service configured by you** and requires internet access and a valid API key. Review that provider's privacy terms before use.

Hari AI is designed to support India-focused responses, including ₹/INR, lakh/crore conventions, IST, and supported Indian languages. It first tries a small set of preferred Flash models and, only if all are unavailable, asks the Gemini API for models available to the supplied key and retries eligible text-generation Flash models. It is optional and is not required for normal browsing.

### Languages and Indian-friendly features

The home dashboard supports 14 language options: English (India), Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi, Urdu, Spanish, French, and Arabic. Language support applies primarily to the home dashboard; it does not mean every webpage or every browser control is translated. The start page also links to related Hari projects and the [@inspiring_creatives_minds Instagram account](https://www.instagram.com/inspiring_creatives_minds/).

## Security and privacy limits

Hari Browser is designed to reduce some common risks, but it does not guarantee anonymity, prevent every tracking technique, or certify the safety of a website or file.

- Websites and search providers still receive the requests needed to deliver content. External services receive data whenever you choose to use them—for example, when you use Hari AI, translate a page with Google Translate, or opt in to reputation checks.
- Private mode helps separate browser data, but cannot erase downloads you explicitly save, screenshots, network/provider logs, or every trace outside the app's control. Exit cleanup is best-effort.
- JavaScript is enabled by default for compatibility and can be disabled in Settings. Blocking HTTP may prevent older sites from loading.
- URL checks, Teen Protection, and file scanning can miss threats or generate false positives. None should be treated as a security certification.
- A previously tested APK has an unresolved Android device-protection alert, as noted above. Investigate that alert before distributing or recommending the affected artifact.

## Get the app

Use only builds linked from the official [Hari Browser repository](https://github.com/harisankar322011-commits/hari-browser).

The repository's release workflow is intended to publish a release APK artifact:

1. Open the repository's **Actions** tab.
2. Open the latest successful **Build Hari Browser Release APK** workflow run on `main`.
3. Download the `hari-browser-2.3.0-release-apk` artifact and read the artifact's notes.
4. Check the APK filename and signing status before installation. An APK named `*-unsigned.apk` is not ready to install.
5. Do not install or redistribute the previously flagged 2.3.0 APK until the Android device-protection alert has been investigated.

For releases intended to be updated in place, maintainers must sign every version with the same private release key. CI must never generate a disposable key; without the stable signing secrets it should publish only an unsigned validation artifact that cannot be installed. Never commit a keystore or signing passwords to source control.

A successful build, CI check, or third-party review does not by itself prove an APK is malware-free. Verify the release artifact, signature, and source before installing it.

## Build from source

### Requirements

- JDK 17
- Gradle 9.6.0
- Android SDK Platform 36
- Android Gradle Plugin 9.4.0 (declared by the project)

This repository currently does not include a Gradle wrapper, so install a compatible Gradle version separately.

### Commands

```bash
git clone https://github.com/harisankar322011-commits/hari-browser.git
cd hari-browser

# Compile a debug APK
gradle :app:assembleDebug

# Run JVM unit tests
gradle :app:testDebugUnitTest

# Run release lint checks
gradle :app:lintRelease
```

Gradle writes APKs under `app/build/outputs/apk/`. A locally built APK should not be assumed to be signed for distribution. Release signing requires a stable, privately stored signing key. Do not use a locally generated test key for public releases.

The repository's GitHub Actions workflow also runs release-build checks, Android lint, Teen Protection unit tests, and source-level security regression checks. Passing these checks improves quality but cannot establish that the app is vulnerability-free.


## Contributing and issue reports

Bug reports and improvement suggestions are welcome through [GitHub Issues](https://github.com/harisankar322011-commits/hari-browser/issues). When reporting a problem, include the app version, Android version, device model, steps to reproduce, and relevant logs with personal information removed. Never include API keys, passwords, or signing credentials in an issue.

## License and third-party components

Hari Browser source code is available under the [MIT License](LICENSE). The ZXing Core dependency used for QR-code functionality is licensed separately under Apache License 2.0; third-party components remain subject to their own licenses.
