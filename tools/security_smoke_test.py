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

    browser = read("app/src/main/java/com/haricalcx/haribrowser/BrowserActivity.java")
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
    ai_client = read("app/src/main/java/com/haricalcx/haribrowser/HariAiClient.java")
    teen_protection = read("app/src/main/java/com/haricalcx/haribrowser/TeenProtection.java")
    teen_provider = read("app/src/main/java/com/haricalcx/haribrowser/TeenProtectionProvider.java")
    teen_tests = read("app/src/test/java/com/haricalcx/haribrowser/TeenProtectionTest.java")
    check("Teen protection menu and protected navigation are wired",
          '"Teen protection · "' in browser and "case 33:" in browser and
          "protectSearchUrl(url)" in browser)
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
          "protectionStore.put(TeenProtection.PIN_HASH_KEY, hash)" in browser and
          "TeenProtection.verifyPin" in browser)
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
    check("Private-process protection reads authoritative state through a non-exported provider",
          'android:name=".TeenProtectionProvider"' in manifest_source and
          'android:exported="false"' in manifest_source and
          "getContentResolver().call(TeenProtectionProvider.CONTENT_URI" in browser and
          "fails closed rather than bypassing protection" in browser and
          "RESULT_ENABLED" in teen_provider)
    check("Private mode cannot modify Teen Protection or its parent PIN",
          "if (privateMode)" in browser[browser.find("private void handleTeenProtectionMenu()"):
                                          browser.find("private void showTeenProtectionSetupDialog()")])
    check("Hari AI home button has a native internal route",
          'href="https://hari-browser-home.invalid/action/ai"' in home_page and 'case "ai":' in browser)
    check("Hari AI API key is sent in a request header over HTTPS",
          'https://generativelanguage.googleapis.com/v1beta/models/' in ai_client and
          'setRequestProperty("x-goog-api-key", apiKey)' in ai_client)
    check("AI provider name is absent from app-facing UI",
          "Gemini" not in browser and "Gemini" not in home_page)
    check("No API key is hard-coded in source",
          re.search(r"AIza[0-9A-Za-z_-]{20,}", browser + ai_client) is None)
    check("Hari AI key uses the encrypted store",
          "secureStore.put(KEY_HARI_AI_API_KEY, value)" in browser and
          'secureStore.get(KEY_HARI_AI_API_KEY, "")' in browser)
    check("No JavaScript-to-native bridge", re.search(r"\.addJavascriptInterface\s*\(", browser) is None)
    check("Debugging is never turned on in source",
          re.search(r"setWebContentsDebuggingEnabled\s*\(\s*true\s*\)", browser) is None)

    secure_store = read("app/src/main/java/com/haricalcx/haribrowser/SecureStore.java")
    private_settings = 'getSharedPreferences(privateMode ? "hari_browser_private_settings" : "hari_browser_settings", MODE_PRIVATE)'
    check("Private settings do not alter standard-browser settings", private_settings in browser)
    check("Private start page receives no standard bookmarks",
          'String bookmarksJson = privateMode ? "[]" : readArray(KEY_BOOKMARKS).toString()' in browser)
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

    check("Encrypted store uses AES-GCM", 'Cipher.getInstance("AES/GCM/NoPadding")' in secure_store)
    check("Encryption key stored in Android Keystore", 'KeyStore.getInstance("AndroidKeyStore")' in secure_store)
    check("Fresh randomized encryption IV required", ".setRandomizedEncryptionRequired(true)" in secure_store)
    check("Storage failure does not fall back to plaintext",
          "Fail closed: do not fall back to storing sensitive data in plaintext." in secure_store)

    gradle = read("app/build.gradle")
    check("Release code shrinking enabled", "minifyEnabled true" in gradle)
    check("Release resource shrinking enabled", "shrinkResources true" in gradle)
    check("Build version advanced for 2.3 release",
          "versionCode 7" in gradle and "versionName '2.3.0'" in gradle)
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
    check("Build workflow repository permission is read-only",
          re.search(r"(?m)^permissions:\s*\n\s+contents:\s*read\s*$", build_workflow) is not None)
    check("APK signature verification enabled in CI",
          "apksigner" in build_workflow and "verify --verbose" in build_workflow)
    check("Release debuggable flag checked in CI",
          "apkanalyzer manifest debuggable" in build_workflow)
    check("APK SHA-256 manifest created in CI", "sha256sum" in build_workflow and "SHA256SUMS.txt" in build_workflow)
    check("APK artifacts publish only from main outside pull requests",
          "if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'" in build_workflow)
    check("Release-only artifact contains no debug APK",
          "assembleRelease" in build_workflow and "testDebugUnitTest" in build_workflow and
          "lint" in build_workflow and "assembleDebug" not in build_workflow and
          "path: dist/*.apk" in build_workflow and "Publish only the release APK" in build_workflow)
    check("Release signing is conditional on protected signing secrets",
          "HARI_RELEASE_KEYSTORE_BASE64" in build_workflow and
          'apksigner" sign' in build_workflow and "--ks-key-alias" in build_workflow)
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
