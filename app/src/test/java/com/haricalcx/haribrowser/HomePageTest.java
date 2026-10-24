package com.haricalcx.haribrowser;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** Regression tests for the integrated home page and its local offline template asset. */
public final class HomePageTest {
    @Test
    public void homePageOffersDedicatedSingularityProtectionDashboard() throws Exception {
        String html = renderHome("[]");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/protections\""));
        assertTrue(html.contains("HARI SINGULARITY Protection Dashboard"));
    }

    @Test
    public void templateAssetIsLocalBoundedAndHasNoUnresolvedRuntimePlaceholders() throws Exception {
        String template = readTemplateAsset();
        assertTrue(template.startsWith("<!doctype html>"));
        assertTrue(template.length() <= 256 * 1024);
        assertTrue(template.contains("const DIRECTORY = __DIRECTORY__;"));

        String html = renderHome("[]");
        for (String placeholder : new String[] {
                "THEME", "__DARKCLASS__", "__MODE__", "__DIRECTORY__", "__BOOKMARKS__",
                "__ENGINE__", "__PRIVATE__", "__LANGUAGE__"
        }) {
            assertFalse("Unresolved template placeholder: " + placeholder, html.contains(placeholder));
        }
        assertTrue(html.contains("https://hari-browser-home.invalid/action/security"));
        assertTrue(html.contains("renderDirectory"));
    }

    @Test
    public void bookmarkTextCannotTerminateTheInlineScriptWhenRendered() throws Exception {
        String unsafeBookmarks = "[{\"title\":\"</script><script>alert(1)</script>\","
                + "\"url\":\"https://example.com/\"}]";
        String html = renderHome(unsafeBookmarks);

        assertFalse(html.contains("</script><script>alert(1)</script>"));
        assertTrue(html.contains("\\u003c/script\\u003e"));
    }

    @Test
    public void curatedDirectoryAcceptsHttpsOnlyAndEscapesScriptMarkup() throws Exception {
        String json = HomePage.directoryJson(
                "Search|Known site|https://example.com/\n" +
                "Search|Insecure site|http://example.com/\n" +
                "Malformed row\n" +
                "Search|<script>|https://safe.example/");
        org.json.JSONArray entries = new org.json.JSONArray(json);

        assertEquals(2, entries.length());
        assertTrue(json.contains("https://example.com/"));
        assertFalse(json.contains("http://example.com/"));
        String escapedScriptName = "\\u003cscript" + "\\u003e";
        assertTrue(json.contains(escapedScriptName));
    }

    @Test
    public void curatedDirectoryRejectsMalformedHttpsAuthoritiesAndEmbeddedCredentials() throws Exception {
        String json = HomePage.directoryJson(
                "Search|Valid|https://example.com/path?q=1\n" +
                "Search|No host|https:///path\n" +
                "Search|Bad port|https://example.com:abc/\n" +
                "Search|Out of range port|https://example.com:99999/\n" +
                "Search|Zero port|https://example.com:0/\n" +
                "Search|Empty port|https://example.com:/\n" +
                "Search|Credentials|https://user:secret@example.com/\n" +
                "Search|No scheme host|https://\n");
        org.json.JSONArray entries = new org.json.JSONArray(json);

        assertEquals(1, entries.length());
        assertEquals("Valid", entries.getJSONObject(0).getString("name"));
        assertEquals("https://example.com/path?q=1", entries.getJSONObject(0).getString("url"));
        assertFalse(json.contains("secret"));
    }

    @Test
    public void siteSecurityDetailsRemainAvailableSeparately() throws Exception {
        String html = renderHome("[]");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/security\""));
    }

    private static String renderHome(String bookmarks) throws IOException {
        return HomePage.renderWithTemplate(readTemplateAsset(),
                "Learning|Example|https://example.com/", false, "DuckDuckGo",
                bookmarks, false, "Ocean", "en");
    }

    private static String readTemplateAsset() throws IOException {
        Path[] candidates = {
                Paths.get("src/main/assets/home_page_template.html"),
                Paths.get("app/src/main/assets/home_page_template.html")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Could not locate bundled home_page_template.html for regression tests.");
    }
}
