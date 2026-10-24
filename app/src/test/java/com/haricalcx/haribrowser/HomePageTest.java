package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Regression tests for the dedicated integrated protection dashboard entry point. */
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
                "Search|Known site|https://example.com/\\n" +
                "Search|Insecure site|http://example.com/\\n" +
                "Malformed row\\n" +
                "Search|<script>|https://safe.example/");
        org.json.JSONArray entries = new org.json.JSONArray(json);

        assertEquals(2, entries.length());
        assertTrue(json.contains("https://example.com/"));
        assertFalse(json.contains("http://example.com/"));
        String escapedScriptName = "\\\\u003cscript" + "\\\\u003e";
        assertTrue(json.contains(escapedScriptName));
    }

    @Test
    public void siteSecurityDetailsRemainAvailableSeparately() {
        String html = HomePage.render(false, "DuckDuckGo", "[]", false, "Ocean", "en");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/security\""));
    }
}
