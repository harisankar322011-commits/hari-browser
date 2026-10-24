package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public final class SearchEngineUrlsTest {
    @Test
    public void mapsEverySelectableEngineToItsSearchEndpoint() {
        assertEquals("https://duckduckgo.com/?q=Kerala+history",
                SearchEngineUrls.searchUrl("DuckDuckGo", "Kerala history"));
        assertEquals("https://www.startpage.com/sp/search?query=Kerala+history",
                SearchEngineUrls.searchUrl("Startpage", "Kerala history"));
        assertEquals("https://search.brave.com/search?q=Kerala+history",
                SearchEngineUrls.searchUrl("Brave Search", "Kerala history"));
        assertEquals("https://www.google.co.in/search?q=Kerala+history",
                SearchEngineUrls.searchUrl("Google", "Kerala history"));
        assertEquals("https://www.bing.com/search?q=Kerala+history",
                SearchEngineUrls.searchUrl("Bing", "Kerala history"));
        assertEquals("https://www.ecosia.org/search?q=Kerala+history",
                SearchEngineUrls.searchUrl("Ecosia", "Kerala history"));
        assertEquals("https://www.mojeek.com/search?q=Kerala+history",
                SearchEngineUrls.searchUrl("Mojeek", "Kerala history"));
        assertEquals("https://www.qwant.com/?q=Kerala+history",
                SearchEngineUrls.searchUrl("Qwant", "Kerala history"));
    }

    @Test
    public void encodesQueryDelimitersSoTheyCannotBecomeNewUrlParameters() {
        String result = SearchEngineUrls.searchUrl("Ecosia", "water bill & pin=secret + Kerala");

        assertTrue(result.contains("water+bill+%26+pin%3Dsecret+%2B+Kerala"));
        assertFalse(result.contains("&pin=secret"));
    }

    @Test
    public void unknownEngineFallsBackToDuckDuckGoAndNullQueryIsSafe() {
        assertEquals("https://duckduckgo.com/?q=example",
                SearchEngineUrls.searchUrl("unknown", "example"));
        assertEquals("https://duckduckgo.com/?q=",
                SearchEngineUrls.searchUrl(null, null));
    }
}
