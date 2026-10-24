package com.haricalcx.haribrowser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public final class UserscriptManagerTest {
    private static final String BASIC_SCRIPT =
            "// ==UserScript==\n" +
            "// @name         Reading helper\n" +
            "// @description  Improves article readability\n" +
            "// @match        https://example.com/articles/*\n" +
            "// @exclude      https://example.com/articles/private/*\n" +
            "// @run-at       document-end\n" +
            "// @grant        none\n" +
            "// ==/UserScript==\n" +
            "document.documentElement.dataset.hariTest = 'loaded';\n";

    @Test
    public void parsesMetadataAndDefaultsImportedScriptsToDisabled() {
        UserscriptManager.Script script = UserscriptManager.parse(BASIC_SCRIPT);

        assertEquals("Reading helper", script.name);
        assertEquals("Improves article readability", script.description);
        assertEquals("document-end", script.runAt);
        assertFalse("Imported scripts must not execute until explicitly enabled", script.enabled);
        assertTrue(script.matchesUrl("https://example.com/articles/math"));
        assertFalse(script.matchesUrl("https://example.com/articles/private/account"));
        assertFalse(script.matchesUrl("https://other.example/articles/math"));
    }

    @Test
    public void supportsExactAndSubdomainHttpsMatches() {
        UserscriptManager.Script script = UserscriptManager.parse(
                "// ==UserScript==\n" +
                "// @name         Example subdomains\n" +
                "// @match        https://*.example.org/*\n" +
                "// @grant        none\n" +
                "// ==/UserScript==\n" +
                "void 0;\n");

        assertTrue(script.matchesUrl("https://example.org/"));
        assertTrue(script.matchesUrl("https://news.example.org/story"));
        assertFalse(script.matchesUrl("http://news.example.org/story"));
        assertFalse(script.matchesUrl("https://news.example.org:8443/story"));
        assertFalse(script.matchesUrl("https://notexample.org/story"));

        List<String> origins = UserscriptManager.allowedOrigins(script);
        assertEquals(2, origins.size());
        assertEquals("https://*.example.org", origins.get(0));
        assertEquals("https://example.org", origins.get(1));
    }

    @Test
    public void rejectsScriptsWithoutExplicitHttpsSiteMatches() {
        assertThrows(IllegalArgumentException.class, () -> UserscriptManager.parse(
                "// ==UserScript==\n// @name Global\n// @match *://*/*\n// ==/UserScript==\nvoid 0;"));
        assertThrows(IllegalArgumentException.class, () -> UserscriptManager.parse(
                "// ==UserScript==\n// @name Global\n// @match https://*/*\n// ==/UserScript==\nvoid 0;"));
        assertThrows(IllegalArgumentException.class, () -> UserscriptManager.parse(
                "// ==UserScript==\n// @name HTTP\n// @match http://example.com/*\n// ==/UserScript==\nvoid 0;"));
    }

    @Test
    public void rejectsUnsupportedPrivilegedApisAndExternalDependencies() {
        assertThrows(IllegalArgumentException.class, () -> UserscriptManager.parse(
                "// ==UserScript==\n// @name Needs GM\n// @match https://example.com/*\n" +
                "// @grant GM_xmlhttpRequest\n// ==/UserScript==\nvoid 0;"));
        assertThrows(IllegalArgumentException.class, () -> UserscriptManager.parse(
                "// ==UserScript==\n// @name Has dependency\n// @match https://example.com/*\n" +
                "// @require https://cdn.example.com/lib.js\n// ==/UserScript==\nvoid 0;"));
    }

    @Test
    public void respectsNoFramesMetadata() {
        UserscriptManager.Script script = UserscriptManager.parse(
                "// ==UserScript==\n" +
                "// @name         Top-frame only\n" +
                "// @match        https://example.com/*\n" +
                "// @noframes\n" +
                "// @grant        none\n" +
                "// ==/UserScript==\n" +
                "void 0;\n");

        assertTrue(script.noFrames);
        assertTrue(UserscriptManager.injectionSource(script).contains("window.top!==window"));
    }

    @Test
    public void generatesAPathCheckedWrapperAndEscapesMetadata() {
        UserscriptManager.Script script = UserscriptManager.parse(BASIC_SCRIPT);
        String wrapper = UserscriptManager.injectionSource(script);

        assertTrue(wrapper.contains("location.href"));
        assertTrue(wrapper.contains("articles/private"));
        assertTrue(wrapper.contains("document-end"));
        assertTrue(wrapper.contains("dataset.hariTest"));
        assertTrue(wrapper.contains("try"));
    }
}
