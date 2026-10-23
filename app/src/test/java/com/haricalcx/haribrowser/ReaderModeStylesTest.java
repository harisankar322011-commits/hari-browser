package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReaderModeStylesTest {
    @Test
    public void readerTypographyUsesSystemFallbacksSuitableForIndicScripts() {
        assertTrue(ReaderModeStyles.CSS.contains("system-ui"));
        assertTrue(ReaderModeStyles.CSS.contains("sans-serif"));
        assertTrue(ReaderModeStyles.CSS.contains("line-height:1.8"));
        assertTrue(ReaderModeStyles.CSS.contains("overflow-wrap:break-word"));
        assertTrue(ReaderModeStyles.CSS.contains("max-width:780px"));
    }

    @Test
    public void styleScriptAddsOneLocalStylesheetWithoutReplacingPageContent() {
        String script = ReaderModeStyles.documentStyleScript();
        assertTrue(script.contains("hari-reader-style"));
        assertTrue(script.contains("createElement('style')"));
        assertTrue(script.contains("s.textContent="));
        assertTrue(script.contains("appendChild(s)"));
        assertFalse(ReaderModeStyles.CSS.contains("display:none"));
    }
}
