# Hari Browser for Android

A privacy-first Android browser built with the Android System WebView.

## Version 2.1.0

Hari Browser 2.1 refreshes the start page with a clearer visual hierarchy, more responsive phone/tablet spacing, refined cards and focus states, broader translations across the nine supported languages, and batched rendering plus debounced directory search for smoother interaction. Choosing a search provider on the home page now saves it as the default. The Settings menu includes an in-app **About Hari Browser / What's new in 2.1** panel, alongside the privacy, tab, download, QR/PDF and local-tool features carried forward from 2.0.

Security claims remain deliberately limited: HTTPS-only navigation, WebView Safe Browsing and local tracker blocking provide useful safeguards, but do not guarantee anonymity or block every tracker. The unsigned release APK is for build validation only; it is not ready for installation or publication.

## Features
- DuckDuckGo search by default; selectable DuckDuckGo, Startpage, Brave Search, Google, or Bing search providers.
- URL-or-search address bar.
- HTTPS-only navigation and blocked cleartext traffic.
- Android WebView Safe Browsing, certificate-error cancellation, and strict mixed-content policy.
- Local ad/tracker host blocking (best-effort, not a complete filter list).
- Third-party cookies disabled; form-password saving and file/content access disabled.
- Separate private-browsing process/profile on Android 9+, with best-effort data cleanup on exit. Private mode cannot view, save, import, remove or export normal-profile bookmarks; its browser preferences use a separate preference store.
- Encrypted bookmarks and optional encrypted history using an Android Keystore AES-GCM key.
- Tabs, duplicate tabs, close-other-tabs, and restore recently closed tabs within the current session.
- Tabs, bookmarks, optional history, page sharing, in-page find, adjustable text size, desktop mode, reader-style mode, and downloads.
- Full-screen web video, web file-upload picker support, stop-loading control, and site security details.
- Generate a QR code locally for the current URL, or use Android's print screen to save a page as PDF.
- Explicit bookmark export/import, with HTTPS-only validation and encrypted on-device storage.
- Refreshed light/dark visual themes with a coordinated indigo-blue palette and five browser accent colours: Ocean, Violet, Mint, Sunset and Rose.
- Responsive home dashboard with Home / Tools / Explore / Privacy panels, quick links, encrypted bookmark tiles, and one-tap browser actions.
- Home dashboard interface translations for English, Malayalam, Hindi, Tamil, Telugu, Kannada, Spanish, French and Arabic, including persistent language choice in Settings and the home-page selector.
- A Hari projects showcase on the start page linking to Hari CalcX, MentalX, VisualX, ChemX, BioQuest, MapX, HariPedia, Hari Editor, Hari GameX, the browser source code and the @inspiring_creatives_minds Instagram account.
- Searchable directory of 286 curated HTTPS destinations across 16 categories; destinations are not preloaded and open only when tapped.
- A curated start-page toolbox presents 8 everyday tools first (calculator, unit converter, local notes, password generator, text toolkit, JSON formatter, URL encoder, and word counter); all 34 built-in local tools remain available through “Show all tools”.
- Add up to 40 personal HTTPS shortcuts stored in the browser profile on the device; no account or external image/script assets are required by the start page.
- Home page's native shortcut actions open tabs, bookmarks, history, downloads, private browsing, find-in-page, QR, print/PDF, share and security controls without adding a JavaScript bridge to external websites. Internal action URLs are accepted only while the local home page is active, so remote pages cannot forge them.
- Redesigned Hari Browser app icon and in-app globe/H logo, including a monochrome adaptive icon on supported Android versions.
- Website image loading can be disabled to save data and reduce third-party requests.
- Private browsing screens are hidden from screenshots and recent-app previews where Android honors secure-window flags.
- Emergency-clear action clears current tabs, cookies, cache, site storage and saved history while retaining bookmarks and explicit downloads.
- Translate the current page through Google Translate (the selected page URL is shared with that external service).
- No app analytics, advertising SDKs, remote blocklist downloads, or account requirement.
- GitHub Actions builds only the release variant and runs Android lint. The workflow publishes one release APK artifact, never a debug APK. When protected signing secrets are configured, the artifact is a signed installable APK; without them, it is explicitly named `HariBrowser-2.1.0-unsigned.apk` and is not installable until signed.

## Build and download the release APK
1. Open the **Actions** tab of this repository.
2. Open the latest successful **Build Hari Browser Release APK** run on `main`.
3. Download the **hari-browser-2.1.0-release-apk** artifact. It contains only the release APK (no debug APK).
4. If the filename ends in `-unsigned.apk`, it is a compile-validated release build but cannot be installed yet. Configure the four protected repository Actions secrets `HARI_RELEASE_KEYSTORE_BASE64`, `HARI_RELEASE_KEY_ALIAS`, `HARI_RELEASE_KEY_PASSWORD`, and `HARI_RELEASE_STORE_PASSWORD` to make CI sign it. Keep the keystore and passwords private; never commit them to source control.

CI checks the release package ID, non-debuggable manifest, declared permissions, APK archive integrity, release size and lint results. When signing secrets are set, CI also verifies the APK signature and publishes a signed release APK. These checks improve build integrity but do not prove the application is vulnerability-free or malware-free. F-Droid should build and sign its own version from the public source. QR generation uses the ZXing Core library, which is Apache-2.0-licensed; that dependency retains its own licence.

## F-Droid
Hari Browser has an open request for packaging in the F-Droid Requests for Packaging tracker: [request #4588](https://gitlab.com/fdroid/rfp/-/work_items/4588). This is a request for review, not acceptance or a published F-Droid listing. The release build, source metadata, dependencies, licensing and reproducibility still need independent review.

Before creating an official release tag, confirm that the release build and lint pass, test the app on a real Android device, review release notes and store screenshots, and ensure the published source exactly matches the release version.

## Security and privacy notes
This project is a privacy-oriented browser, not a guarantee of anonymity or perfect protection. Websites you visit and your chosen search provider still receive the requests needed to serve pages. Android System WebView and Safe Browsing are provided by the device. The local blocker is deliberately conservative and cannot block every tracker. Private mode separates WebView data on supported Android versions, hides normal-profile bookmarks, blocks bookmark import/export/save/remove in private sessions, uses a separate browser-settings preference file and clears common site data on exit. This cleanup is best-effort: it cannot erase downloads you explicitly save, screenshots, network/provider logs, or every artifact outside the app's control.

The browser intentionally rejects HTTP instead of silently downgrading security. Some older sites will not open. JavaScript is enabled by default for compatibility and can be disabled in Settings. Review the source and build output before relying on this for sensitive activities.

## Build requirements
- Android SDK Platform 36
- JDK 17
- Gradle 9.6.0
- Android Gradle Plugin 9.4.0

## License
MIT
