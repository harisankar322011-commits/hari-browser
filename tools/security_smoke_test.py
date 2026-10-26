#!/usr/bin/env python3
"""Fast source-level regression checks for Hari Browser's security baseline.

These checks prevent accidental removal of key protections. They are not a
substitute for Android runtime testing, CodeQL, a malware scan, or an audit.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
checks: list[tuple[str, bool, str]] = []


def check(name: str, condition: bool, detail: str = "") -> None:
    checks.append((name, bool(condition), detail))


def read(relative: str) -> str:
    path = ROOT / relative
    return path.read_text(encoding="utf-8")


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        return ""
    end = source.find("\n    private ", start + len(signature))
    if end < 0:
        end = len(source)
    return source[start:end]


try:
    manifest = ET.fromstring(read("app/src/main/AndroidManifest.xml"))
    application = manifest.find("application")
    permissions = {
        element.get(ANDROID + "name", "")
        for element in manifest.findall("uses-permission")
    }
    check("Only INTERNET permission declared", permissions == {"android.permission.INTERNET"},
          f"Declared permissions: {sorted(permissions)}")
    check("Android app backup disabled",
          application is not None and application.get(ANDROID + "allowBackup") == "false")
    check("Full backup disabled",
          application is not None and application.get(ANDROID + "fullBackupContent") == "false")
    check("Cleartext traffic disabled",
          application is not None and application.get(ANDROID + "usesCleartextTraffic") == "false")
    check("Network security config attached",
          application is not None and application.get(ANDROID + "networkSecurityConfig") == "@xml/network_security_config")

    network_config = ET.fromstring(read("app/src/main/res/xml/network_security_config.xml"))
    base = network_config.find("base-config")
    anchors = base.find("trust-anchors") if base is not None else None
    check("Cleartext denied by network security config",
          base is not None and base.get("cleartextTrafficPermitted") == "false")
    check("Only system trust anchors configured",
          anchors is not None and [x.get("src") for x in anchors.findall("certificates")] == ["system"])

    signature_engine = read("app/src/main/java/com/haricalcx/haribrowser/MalwareSignatureEngine.java")
    signature_tests = read("app/src/test/java/com/haricalcx/haribrowser/MalwareSignatureEngineTest.java")
    download_scanner = read("app/src/main/java/com/haricalcx/haribrowser/DownloadedFileScanner.java")
    antivirus_engine = read("app/src/main/java/com/haricalcx/haribrowser/AntivirusEngine.java")
    antivirus_ui = read("app/src/main/java/com/haricalcx/haribrowser/AntivirusActivity.java")
    # Build the known antivirus test marker from fragments so this CI guard does
    # not embed the contiguous marker in its own source.
    test_marker = ("X5O!P%@AP[" + "4\\PZX54(P^)7CC)7}$" +
                   "EICAR-" + "STANDARD-ANTIVIRUS-TEST-FILE!$H+H*")
    check("Production scanner excludes the standard AV test marker",
          test_marker not in download_scanner and
          test_marker not in antivirus_engine and
          test_marker not in antivirus_ui and
          "MALICIOUS_TEST_SIGNATURE" not in download_scanner and
          "MALICIOUS_TEST_SIGNATURE" not in antivirus_engine)
    check("Offline multi-indicator malware signature engine is wired into stream scanning",
          "Aho-Corasick" in signature_engine and
          "MalwareSignatureEngine.newScanner()" in download_scanner and
          "signatureMatcher.update(buffer, count)" in download_scanner and
          "PHP_WEBSHELL_INDICATORS" in signature_engine and
          "POWERSHELL_DOWNLOAD_EXECUTE" in signature_engine)
    check("ZIP entries also receive bounded streaming signature inspection",
          "MalwareSignatureEngine.StreamMatcher signatureMatcher" in antivirus_engine and
          "signatureMatcher.finish()" in antivirus_engine and
          "MAX_EXPANDED_ARCHIVE_BYTES" in antivirus_engine)
    check("Malware signature rules have automated combination and chunk-boundary tests",
          "multiIndicatorSignatureRequiresReviewWithoutDeclaringMalware" in
          read("app/src/test/java/com/haricalcx/haribrowser/DownloadedFileScannerTest.java") and
          "matcherFindsPatternsSplitAcrossChunks" in signature_tests and
          "benignSingleIndicatorsDoNotTriggerACombinationRule" in signature_tests and
          "multiIndicatorSignatureIsDetectedInsideZipEntries" in
          read("app/src/test/java/com/haricalcx/haribrowser/AntivirusEngineTest.java"))

    browser = read("app/src/main/java/com/haricalcx/haribrowser/BrowserActivity.java")
    check("BrowserActivity has no synchronous SecureStore reads or obsolete array reader",
          "secureStore.get(" not in browser and "private JSONArray readArray(" not in browser)
    search_engine_urls = read("app/src/main/java/com/haricalcx/haribrowser/SearchEngineUrls.java")
    for label, fragment in [
        ("WebView file access disabled", "web.setAllowFileAccess(false)"),
        ("WebView content access disabled", "web.setAllowContentAccess(false)"),
        ("File-origin access disabled", "web.setAllowFileAccessFromFileURLs(false)"),
        ("Universal file-origin access disabled", "web.setAllowUniversalAccessFromFileURLs(false)"),
        ("Mixed content denied", "WebSettings.MIXED_CONTENT_NEVER_ALLOW"),
        ("Geolocation disabled", "web.setGeolocationEnabled(false)"),
        ("Safe Browsing enabled", "web.setSafeBrowsingEnabled(true)"),
        ("Third-party cookies disabled", "setAcceptThirdPartyCookies(webView, false)"),
        ("WebView debugging disabled", "WebView.setWebContentsDebuggingEnabled(false)"),
        ("TLS errors canceled", "handler.cancel()"),
        ("Safe Browsing returns to safety", "callback.backToSafety(true)"),
        ("Site permissions denied by default", "request.deny()"),
        ("Document-start protection uses WebKit support API", "WebViewCompat.addDocumentStartJavaScript"),
    ]:
        check(label, fragment in browser)
    home_page = read("app/src/main/java/com/haricalcx/haribrowser/HomePage.java")
    home_page_template = read("app/src/main/assets/home_page_template.html")
    home_page_content = home_page + "\n" + home_page_template
    home_directory = read("app/src/main/assets/home_directory.txt")
    ai_client = read("app/src/main/java/com/haricalcx/haribrowser/HariAiClient.java")
    teen_protection = read("app/src/main/java/com/haricalcx/haribrowser/TeenProtection.java")
    teen_provider = read("app/src/main/java/com/haricalcx/haribrowser/TeenProtectionProvider.java")
    teen_tests = read("app/src/test/java/com/haricalcx/haribrowser/TeenProtectionTest.java")
    browser_ui_text = read("app/src/main/java/com/haricalcx/haribrowser/BrowserUiText.java")
    browser_ui_text_tests = read("app/src/test/java/com/haricalcx/haribrowser/BrowserUiTextTest.java")
    check("TLS certificate failures use the selected-language warning",
          "showToast(ui(\"Blocked: the site's security certificate is invalid.\"))" in browser and
          "TLS_CERTIFICATE_WARNING_KEY" in browser_ui_text and
          "addOne(all, \"ml\", TLS_CERTIFICATE_WARNING_KEY" in browser_ui_text and
          "translatesTlsCertificateWarningForSupportedNonEnglishLocales" in browser_ui_text_tests)
    check("Teen protection menu and protected navigation are wired",
          'ui("Teen protection")' in browser and '"Teen protection"' in browser_ui_text and
          "case 33:" in browser and "protectSearchUrl(url)" in browser)
    check("Teen protection label includes Indian-language translations",
          'addExtras(all, "ml"' in browser_ui_text and '"ടീൻ സംരക്ഷണം"' in browser_ui_text and
          'addExtras(all, "hi"' in browser_ui_text and '"किशोर सुरक्षा"' in browser_ui_text)
    check("Teen protection filters restricted URLs in main-frame requests",
          "request.isForMainFrame()" in browser and
          "TeenProtection.isBlockedUrl(requestUri.toString())" in browser)
    check("Teen protection blocks restricted direct domains and obfuscated query terms",
          "BLOCKED_DOMAINS" in teen_protection and "static boolean isBlockedDomain" in teen_protection and
          "foldLeetspeak" in teen_protection and "porn.com" in teen_protection and "bet365.com" in teen_protection)
    check("Teen protection preserves common help-seeking and health queries",
          "SAFE_SUPPORT_PHRASES" in teen_protection and "porn addiction" in teen_protection and
          "gambling support" in teen_protection)
    check("Teen protection JUnit tests cover direct URLs and obfuscated search terms",
          "blocksRestrictedAdultAndGamblingDomainsButNotLookalikeHosts" in teen_tests and
          "p.o.r.n videos" in teen_tests and "porn addiction help" in teen_tests)
    check("Teen protection uses a parent PIN stored as salted PBKDF2 hash",
          "PBKDF2WithHmacSHA256" in teen_protection and
          "protectionStore.putAsync(TeenProtection.PIN_HASH_KEY, hash)" in browser and
          "TeenProtection.verifyPin" in browser)
    check("Teen Protection PIN storage is asynchronous and encrypted",
          "protectionStore.getAsync(TeenProtection.PIN_SALT_KEY" in browser and
          "protectionStore.getAsync(TeenProtection.PIN_HASH_KEY" in browser and
          "protectionStore.putAsync(TeenProtection.PIN_SALT_KEY, salt)" in browser and
          "protectionStore.putAsync(TeenProtection.PIN_HASH_KEY, hash)" in browser and
          "protectionStore.get(TeenProtection.PIN_SALT_KEY" not in browser and
          "protectionStore.get(TeenProtection.PIN_HASH_KEY" not in browser and
          "protectionStore.put(TeenProtection.PIN_SALT_KEY" not in browser and
          "protectionStore.put(TeenProtection.PIN_HASH_KEY" not in browser)
    teen_setup_body = method_body(browser, "private void showTeenProtectionSetupDialog()")
    teen_verify_body = method_body(browser, "private void verifyTeenProtectionPin(")
    check("Teen Protection PBKDF2 setup and verification stay off the UI thread",
          "StorageTaskQueue.execute(() -> {" in teen_setup_body and
          "TeenProtection.hashPin(pin, salt)" in teen_setup_body and
          'protectionStore.getAsync(TeenProtection.PIN_SALT_KEY, "", Runnable::run' in teen_verify_body and
          'protectionStore.getAsync(TeenProtection.PIN_HASH_KEY, "", Runnable::run' in teen_verify_body and
          "TeenProtection.verifyPin(candidate, salt, hash)" in teen_verify_body)
    check("Teen Protection lockout counts failed PINs even after dialog dismissal",
          "TeenProtectionPinLockout.afterFailedAttempt(" in teen_verify_body and
          teen_verify_body.find("editor.apply();") >= 0 and
          teen_verify_body.find("editor.apply();") <
          teen_verify_body.find("if (!isTeenPinDialogActive(dialog)) return;", teen_verify_body.find("boolean verified =")))

    pin_lockout = read("app/src/main/java/com/haricalcx/haribrowser/TeenProtectionPinLockout.java")
    pin_lockout_tests = read("app/src/test/java/com/haricalcx/haribrowser/TeenProtectionPinLockoutTest.java")
    check("Teen Protection lockout threshold and expiry are unit tested",
          "MAX_ATTEMPTS = 5" in pin_lockout and
          "LOCKOUT_MILLIS = 60_000L" in pin_lockout and
          "fifthFailureStartsOneMinuteLockoutAndResetsFailureCounter" in pin_lockout_tests and
          "remainingLockoutTimeIsRoundedUpAndNeverNegative" in pin_lockout_tests)
    check("Teen protection applies strict SafeSearch parameters",
          'key = "safe"' in teen_protection and 'key = "adlt"' in teen_protection and
          'key = "kp"' in teen_protection and 'key = "safesearch"' in teen_protection)
    check("Brave strict preference cookie is set during protected navigation",
          "TeenProtection.isBraveSearchHostUrl(url)" in browser and
          'setCookie("https://search.brave.com/", "safesearch=strict;' in browser)
    check("Teen protection documents provider and device-level limitations",
          "Startpage's filtering is controlled by Startpage itself" in read("README.md") and
          "not a complete content filter" in teen_protection)
    manifest_source = read("app/src/main/AndroidManifest.xml")
    external_link_policy = read("app/src/main/java/com/haricalcx/haribrowser/ExternalLinkPolicy.java")
    check("External scheme policy explicitly allows only validated HTTP(S), mailto and tel",
          'case "mailto":' in external_link_policy and 'case "tel":' in external_link_policy and
          'default:' in external_link_policy and 'return Kind.BLOCKED;' in external_link_policy and
          "safeMailTarget(input)" in external_link_policy and "safePhoneTarget(input)" in external_link_policy)
    check("External mail and telephone links require confirmation and never place calls",
          "confirmExternalLink(url)" in browser and
          'setPositiveButton(mail ? "Open email app" : "Open dialer"' in browser and
          "Intent.ACTION_SENDTO" in browser and "Intent.ACTION_DIAL" in browser and
          "Intent.ACTION_CALL" not in browser and "android.permission.CALL_PHONE" not in manifest_source)
    check("External links are reduced to canonical recipient/number before launch",
          "ExternalLinkPolicy.safeExternalUri(input)" in browser and
          "private static String safeMailTarget(String input)" in external_link_policy and
          "private static String safePhoneTarget(String input)" in external_link_policy)
    check("Private-process protection reads authoritative state through a non-exported provider",
          'android:name=".TeenProtectionProvider"' in manifest_source and
          'android:exported="false"' in manifest_source and
          "getContentResolver().call(TeenProtectionProvider.CONTENT_URI" in browser and
          "fails closed rather than bypassing protection" in browser and
          "RESULT_ENABLED" in teen_provider)
    check("Private mode cannot modify Teen Protection or its parent PIN",
          "if (privateMode)" in browser[browser.find("private void handleTeenProtectionMenu()"):
                                          browser.find("private void showTeenProtectionSetupDialog()")])
    check("Home directory data is a bounded offline asset with strict HTTPS URL parsing",
          '"home_directory.txt"' in home_page and
          "readDirectoryAsset" in home_page and
          "directoryJson(directorySource)" in home_page and
          "HomePage.render(this," in browser and
          'if (total > 65536)' in home_page and
          "new URI(url)" in home_page and
          "parsed.getUserInfo() != null" in home_page and
          'authority.endsWith(":")' in home_page and
          "port < 1" in home_page and "port > 65535" in home_page)
    check("India public services directory includes verified official portals",
          "India services|DigiLocker — Digital Documents|https://www.digilocker.gov.in/" in home_directory and
          "India services|UIDAI — Aadhaar|https://uidai.gov.in/" in home_directory and
          "India services|UMANG — Government Services|https://web.umang.gov.in/" in home_directory and
          "India services|DIKSHA — School Learning|https://diksha.gov.in/" in home_directory and
          "India services|Kerala Government|https://kerala.gov.in/" in home_directory)
    check("Five additional Indian-script home languages are available in the page UI",
          all(code + ":" in home_page_template for code in ("bn", "mr", "gu", "pa", "ur")) and
          all(('value="' + code + '"') in home_page_template for code in ("bn", "mr", "gu", "pa", "ur")))
    check("Native language settings persist all five new Indian languages",
          all(('"' + code + '"') in browser[browser.find("private void setHomeLanguage"):browser.find("private void setHomeSearchEngine")]
              for code in ("bn", "mr", "gu", "pa", "ur")) and
          all(name in browser for name in ("Bengali (বাংলা)", "Marathi (मराठी)", "Gujarati (ગુજરાતી)", "Punjabi (ਪੰਜਾਬੀ)", "Urdu (اردو)")))
    check("Indian home languages use India date formatting and Urdu is right-to-left",
          "en-IN" in home_page_template and "currentLanguage+'-IN'" in home_page_template and
          "['ar','ur'].includes(currentLanguage)" in home_page_template)
    check("Indian language actions remain translated instead of falling back to English",
          "EXTRA_UI[code].actions = words.actions || EXTRA_UI.en.actions" in home_page_template and
          "＋ নতুন ট্যাব" in home_page_template and "＋ नवीन टॅब" in home_page_template and
          "＋ નવી ટૅબ" in home_page_template and "＋ ਨਵੀਂ ਟੈਬ" in home_page_template and "＋ نیا ٹیب" in home_page_template)
    check("India services category is translated on both category buttons and site cards",
          "function displayDirectoryCategory(category)" in home_page_template and
          "b.textContent=displayDirectoryCategory(c)" in home_page_template and
          "esc(displayDirectoryCategory(site.category))" in home_page_template)
    check("Google selection prefers the India search endpoint",
          "google.co.in/search?q=" in home_page_template and
          'case "Google":' in search_engine_urls and
          "google.co.in/search?q=" in search_engine_urls)
    check("Hari AI home button has a native internal route",
          'href="https://hari-browser-home.invalid/action/ai"' in home_page_template and 'case "ai":' in browser)
    check("Hari AI prompt supports Indian context and major Indian languages",
          "Indian numbering such as lakh and crore" in ai_client and
          "Indian Standard Time" in ai_client and "₹ (INR)" in ai_client and
          all(lang in ai_client for lang in ("Malayalam", "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu")))
    check("Hari AI API key is sent in a request header over HTTPS",
          'https://generativelanguage.googleapis.com/v1beta/models/' in ai_client and
          'setRequestProperty("x-goog-api-key", apiKey)' in ai_client)
    ai_tests = read("app/src/test/java/com/haricalcx/haribrowser/HariAiClientTest.java")
    check("Hari AI tests a key with an explicit non-blocking connection check",
          "HariAiClient.testConnection(keyToTest)" in browser and
          '"HariAI-key-test"' in browser and "Testing sends one short prompt" in browser)
    check("Hari AI falls back across stable supported model identifiers only on HTTP 404",
          '"gemini-3.5-flash-lite"' in ai_client and '"gemini-3.1-flash-lite"' in ai_client and
          '"gemini-3.6-flash"' in ai_client and "catch (ModelUnavailableException unavailable)" in ai_client)
    check("Hari AI presents actionable API-key and quota errors",
          "httpStatusMessage(int status)" in ai_client and
          "API key is invalid or expired" in ai_client and
          "usage limit was reached" in ai_client)
    check("Hari AI regression tests cover endpoints and error explanations",
          "usesDocumentedGenerateContentEndpointShape" in ai_tests and
          "explainsCommonApiKeyAndQuotaErrors" in ai_tests)
    check("AI provider name is absent from app-facing UI",
          "Gemini" not in browser and "Gemini" not in home_page_content)
    check("No API key is hard-coded in source",
          re.search(r"AIza[0-9A-Za-z_-]{20,}", browser + ai_client) is None)
    check("Hari AI key uses asynchronous encrypted storage",
          "secureStore.putAsync(KEY_HARI_AI_API_KEY, value)" in browser and
          'secureStore.getAsync(KEY_HARI_AI_API_KEY, "", getMainExecutor()' in browser and
          'secureStore.updateAsync(KEY_HARI_AI_API_KEY, "", stored -> ""' in browser)
    check("URLhaus API key uses asynchronous encrypted storage",
          'protectionStore.getAsync("hari_rep_store_1", "", getMainExecutor()' in browser and
          'protectionStore.get("hari_rep_store_1", "")' not in browser)
    urlhaus_state = read("app/src/main/java/com/haricalcx/haribrowser/UrlhausSessionState.java")
    urlhaus_state_tests = read("app/src/test/java/com/haricalcx/haribrowser/UrlhausSessionStateTest.java")
    check("URLhaus session cache and concurrent lookup count are bounded",
          "new UrlhausSessionState()" in browser and
          "urlhausSessionState.beginLookup(url)" in browser and
          "urlhausSessionState.cachedResult(url)" in browser and
          "urlhausSessionState.finishLookup(url)" in browser and
          "MAX_PENDING_LOOKUPS = 4" in urlhaus_state and
          "MAX_CACHED_RESULTS = 128" in urlhaus_state and
          "cachesOnlyDefinitiveStatusesAndCapsCacheWithLeastRecentlyUsedEviction" in urlhaus_state_tests and
          "admitsOnlyFourConcurrentLookupsAndRejectsDuplicates" in urlhaus_state_tests)
    check("No JavaScript-to-native bridge in the general browser WebView",
          re.search(r"\.addJavascriptInterface\s*\(", browser) is None)
    crypto_html = read("app/src/main/assets/hari_cryptography.html")
    crypto_activity = read("app/src/main/java/com/haricalcx/haribrowser/CryptographyActivity.java")
    crypto_policy = read("app/src/main/java/com/haricalcx/haribrowser/CryptographySecurityPolicy.java")
    check("Cryptography page uses local authenticated encryption and denies remote connections",
          "AES-GCM" in crypto_html and "PBKDF2-HMAC-SHA-256" in crypto_html and
          "connect-src 'none'" in crypto_html and "frame-src 'none'" in crypto_html and
          "MAX_BYTES = 8 * 1024 * 1024" in crypto_html)
    check("Cryptography WebView is restricted to its bundled asset origin",
          "WebViewAssetLoader" in crypto_activity and
          "settings.setAllowFileAccess(false)" in crypto_activity and
          "settings.setAllowContentAccess(false)" in crypto_activity and
          "shouldInterceptRequest" in crypto_activity and
          "CryptographySecurityPolicy.isAllowedAssetUrl" in crypto_activity and
          'ASSET_HOST = "appassets.androidplatform.net"' in crypto_policy)
    check("Cryptography save bridge is bounded and invokes Android's save picker",
          "MAX_BRIDGE_BASE64_CHARS" in crypto_activity and
          "MAX_OUTPUT_BYTES" in crypto_activity and
          "Intent.ACTION_CREATE_DOCUMENT" in crypto_activity and
          "Arrays.fill(decoded, (byte) 0)" in crypto_activity)
    check("Cryptography toolbox action reaches its dedicated native screen",
          "action/cryptography" in home_page_template and 'case "cryptography":' in browser)
    check("Debugging is never turned on in source",
          re.search(r"setWebContentsDebuggingEnabled\s*\(\s*true\s*\)", browser) is None)

    secure_store = read("app/src/main/java/com/haricalcx/haribrowser/SecureStore.java")
    private_settings = 'getSharedPreferences(privateMode ? "hari_browser_private_settings" : "hari_browser_settings", MODE_PRIVATE)'
    check("Private settings do not alter standard-browser settings", private_settings in browser)
    check("Private start page receives no standard bookmarks",
          'renderHome(tab, "[]");' in browser and
          'secureStore.getAsync(KEY_BOOKMARKS, "[]", getMainExecutor()' in browser and
          'renderHome(tab, parseArray(serialized).toString())' in browser and
          'String bookmarksJson = privateMode ? "[]" : readArray(KEY_BOOKMARKS).toString()' not in browser)
    check("Browser constructs a profile-specific encrypted store",
          "new SecureStore(this, privateMode)" in browser)
    check("Private encrypted-store namespace and key are isolated",
          'String suffix = privateProfile ? ".private" : ""' in secure_store and
          "keyAlias = KEY_ALIAS + suffix" in secure_store and
          'PREFS_NAME + suffix' in secure_store)

    for name, signature in [
        ("private bookmark save blocked", "private void addBookmark()"),
        ("private bookmark listing blocked", "private void showBookmarks()"),
        ("private bookmark removal blocked", "private void removeBookmark(String url)"),
        ("private bookmark export blocked", "private void exportBookmarks()"),
        ("private bookmark import UI blocked", "private void importBookmarks()"),
        ("private bookmark import operation blocked", "private void mergeImportedBookmarks(String source)"),
        ("private history write blocked", "private void rememberHistory(String title, String url)"),
    ]:
        body = method_body(browser, signature)
        check(name, "if (privateMode)" in body and "return;" in body)

    check("Offline reading persistence runs through async encrypted storage",
          "secureStore.updateAsync(KEY_READ_LATER" in browser and
          "readOfflinePagesAsync" in browser and
          "secureStore.get(KEY_READ_LATER" not in browser and
          "secureStore.put(KEY_READ_LATER" not in browser)
    check("Encrypted store uses AES-GCM", 'Cipher.getInstance("AES/GCM/NoPadding")' in secure_store)
    check("Encryption key stored in Android Keystore", 'KeyStore.getInstance("AndroidKeyStore")' in secure_store)
    check("Fresh randomized encryption IV required", ".setRandomizedEncryptionRequired(true)" in secure_store)
    check("Storage failure does not fall back to plaintext",
          "Fail closed: do not fall back to storing sensitive data in plaintext." in secure_store)

    gradle = read("app/build.gradle")
    check("Release code shrinking enabled", "minifyEnabled true" in gradle)
    check("Release resource shrinking enabled", "shrinkResources true" in gradle)
    check("Build version advanced for 2.4 family release",
          "versionCode 8" in gradle and "versionName '2.4.0'" in gradle)
    check("Teen protection JUnit dependency configured",
          "testImplementation 'junit:junit:4.13.2'" in gradle)
    check("CI runs release unit tests",
          "testDebugUnitTest" in read(".github/workflows/build-apk.yml"))

    for workflow_name in [
        ".github/workflows/build-apk.yml",
        ".github/workflows/codeql.yml",
        ".github/workflows/dependency-review.yml",
        ".github/workflows/secret-scan.yml",
    ]:
        workflow = read(workflow_name)
        actions = re.findall(r"^\s*uses:\s*([^\s#]+)", workflow, flags=re.MULTILINE)
        unpinned = [action for action in actions
                    if "@" not in action or not re.fullmatch(r"[0-9a-fA-F]{40}", action.rsplit("@", 1)[-1])]
        check(f"Third-party actions pinned to full commit SHA ({workflow_name})",
              bool(actions) and not unpinned, f"Unpinned actions: {unpinned}")
        check(f"Checkout credentials not persisted ({workflow_name})",
              "actions/checkout@" not in workflow or "persist-credentials: false" in workflow)
    build_workflow = read(".github/workflows/build-apk.yml")
    debug_build_workflow = read(".github/workflows/build-debug-apk.yml")
    codeql_workflow = read(".github/workflows/codeql.yml")
    wrapper_properties = read("gradle/wrapper/gradle-wrapper.properties")
    check("Every Android CI build uses the checksum-pinned Gradle wrapper",
          "distributionSha256Sum=" in wrapper_properties and
          "./gradlew --no-daemon" in build_workflow and
          "./gradlew --no-daemon" in debug_build_workflow and
          "./gradlew --no-daemon" in codeql_workflow and
          not re.search(r"(?m)^\s*gradle --no-daemon", build_workflow + debug_build_workflow + codeql_workflow) and
          "gradle-version:" not in build_workflow + debug_build_workflow + codeql_workflow)
    check("Build workflow repository permission is read-only",
          re.search(r"(?m)^permissions:\s*\n\s+contents:\s*read\s*$", build_workflow) is not None)
    check("APK signature verification enabled in CI",
          "apksigner" in build_workflow and "verify --verbose" in build_workflow)
    check("Release debuggable flag checked in CI",
          "apkanalyzer manifest debuggable" in build_workflow)
    check("APK SHA-256 manifest created in CI", "sha256sum" in build_workflow and "SHA256SUMS.txt" in build_workflow)
    check("APK artifacts publish only from main outside pull requests",
          "if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'" in build_workflow)
    check("Release artifact publishes only from main and excludes debug APKs",
          "assembleRelease" in build_workflow and "testDebugUnitTest" in build_workflow and
          "lint" in build_workflow and
          "if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'" in build_workflow and
          "path: dist/*.apk" in build_workflow and "Publish only the release APK" in build_workflow)
    check("Installable debug APK is uploaded only for pull-request device testing",
          "Build installable debug APK for pull-request testing" in build_workflow and
          "Validate debug APK for pull-request testing" in build_workflow and
          "Upload installable debug APK for pull-request testing" in build_workflow and
          "if: github.event_name == 'pull_request'" in build_workflow and
          "path: app/build/outputs/apk/debug/app-debug.apk" in build_workflow)
    check("Debug test APK package ID and debuggable flag are validated",
          'com.haricalcx.haribrowser.debug' in build_workflow and
          'apkanalyzer manifest debuggable "$apk"' in build_workflow and
          'test "$debuggable" = "true"' in build_workflow)
    check("Release signing is conditional on protected signing secrets and a pinned certificate",
          "HARI_RELEASE_KEYSTORE_BASE64" in build_workflow and
          "HARI_RELEASE_CERT_SHA256" in build_workflow and
          'apksigner" sign' in build_workflow and "--ks-key-alias" in build_workflow and
          "certificate SHA-256 digest" in build_workflow and
          'actual_fingerprint" != "$expected_fingerprint"' in build_workflow)
    check("One-time signing is restricted to same-repository PR owner testing",
          "One-time sign release APK for owner testing" in build_workflow and
          "if: github.event_name == 'pull_request' && github.event.pull_request.head.repo.full_name == github.repository" in build_workflow and
          "hari-browser-one-time-$GITHUB_RUN_ID.jks" in build_workflow and
          "keytool -genkeypair" in build_workflow and
          "Upload one-time-signed release APK for owner testing" in build_workflow and
          "retention-days: 3" in build_workflow and
          "Stable release signing is required." in build_workflow)
    check("Missing stable signer fails release build instead of publishing unsigned APK",
          "Stable release signing is required." in build_workflow and
          "dist/HariBrowser-2.4.0-unsigned.apk" not in build_workflow and
          'publish_apk="dist/HariBrowser-2.4.0.apk"' in build_workflow)
    secret_scan_workflow = read(".github/workflows/secret-scan.yml")
    check("Secret scanner uses complete Git history", "fetch-depth: 0" in secret_scan_workflow)
    check("Secret scanner does not persist checkout credentials",
          "persist-credentials: false" in secret_scan_workflow)
    check("Secret scan cannot post PR comments",
          "GITLEAKS_ENABLE_COMMENTS: false" in secret_scan_workflow)
    check("Secret scan does not upload findings as artifacts",
          "GITLEAKS_ENABLE_UPLOAD_ARTIFACT: false" in secret_scan_workflow)

except Exception as exc:  # Keep the failing check output useful in CI.
    print(f"ERROR: security smoke checks could not complete: {exc}", file=sys.stderr)
    raise

failed = [item for item in checks if not item[1]]
for name, passed, detail in checks:
    print(f"[{'PASS' if passed else 'FAIL'}] {name}" + (f" — {detail}" if detail and not passed else ""))
print(f"\n{len(checks) - len(failed)}/{len(checks)} security smoke checks passed.")
if failed:
    sys.exit(1)
