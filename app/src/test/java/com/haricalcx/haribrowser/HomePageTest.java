package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

/** Regression tests for the integrated home-page protection and offline directory. */
public final class HomePageTest {
    @Test
    public void homePageOffersDedicatedSingularityProtectionDashboard() {
        String html = HomePage.render(false, "DuckDuckGo", "[]", false, "Ocean", "en");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/protections\""));
        assertTrue(html.contains("HARI SINGULARITY Protection Dashboard"));
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
                "Search|Valid|https://example.com/path?q=1\\n" +
                "Search|No host|https:///path\\n" +
                "Search|Bad port|https://example.com:abc/\\n" +
                "Search|Out of range port|https://example.com:99999/\\n" +
                "Search|Credentials|https://user:secret@example.com/\\n" +
                "Search|No scheme host|https://\\n");
        org.json.JSONArray entries = new org.json.JSONArray(json);

        assertEquals(1, entries.length());
        assertEquals("Valid", entries.getJSONObject(0).getString("name"));
        assertEquals("https://example.com/path?q=1", entries.getJSONObject(0).getString("url"));
        assertFalse(json.contains("secret"));
    }

    @Test
    public void siteSecurityDetailsRemainAvailableSeparately() {
        String html = HomePage.render(false, "DuckDuckGo", "[]", false, "Ocean", "en");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/security\""));
    }
}
