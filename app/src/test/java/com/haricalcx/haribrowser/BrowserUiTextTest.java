package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BrowserUiTextTest {
    @Test
    public void translatesCoreControlsForSelectedIndianLanguages() {
        assertEquals("പോകുക", BrowserUiText.translate("ml", "GO"));
        assertEquals("जाएँ", BrowserUiText.translate("hi-IN", "GO"));
        assertEquals("செல்", BrowserUiText.translate("ta", "GO"));
        assertEquals("ವಾಯ್ಸ್", BrowserUiText.translate("kn", "Voice search"));
    }

    @Test
    public void keepsUnknownAndUntranslatedLabelsInEnglish() {
        assertEquals("Custom label", BrowserUiText.translate("ml", "Custom label"));
        assertEquals("Settings", BrowserUiText.translate("unknown", "Settings"));
        assertEquals("", BrowserUiText.translate("ml", ""));
    }

    @Test
    public void choosesRecognitionLocaleFromSelectedDashboardLanguage() {
        assertEquals("ml-IN", BrowserUiText.speechLocale("ml"));
        assertEquals("hi-IN", BrowserUiText.speechLocale("hi"));
        assertEquals("ta-IN", BrowserUiText.speechLocale("ta"));
        assertEquals("en-IN", BrowserUiText.speechLocale("en"));
        assertEquals("fr-FR", BrowserUiText.speechLocale("fr"));
        assertEquals("en-IN", BrowserUiText.speechLocale(null));
    }
}
