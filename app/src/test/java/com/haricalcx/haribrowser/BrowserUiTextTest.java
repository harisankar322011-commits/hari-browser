package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BrowserUiTextTest {
    @Test
    public void translatesCoreControlsForSelectedIndianLanguages() {
        assertEquals("പോകുക", BrowserUiText.translate("ml", "GO"));
        assertEquals("जाएँ", BrowserUiText.translate("hi-IN", "GO"));
        assertEquals("செல்", BrowserUiText.translate("ta", "GO"));
        assertEquals("ಧ್ವನಿ ಹುಡುಕಾಟ", BrowserUiText.translate("kn", "Voice search"));
        assertEquals("പരസ്യങ്ങളും ട്രാക്കറുകളും തടയുക",
                BrowserUiText.translate("ml", "Block ads and trackers"));
        assertEquals("विज्ञापन और ट्रैकर रोकें",
                BrowserUiText.translate("hi", "Block ads and trackers"));
        assertEquals("Bloquear anuncios y rastreadores",
                BrowserUiText.translate("es", "Block ads and trackers"));
        assertEquals("الإعدادات الخاصة", BrowserUiText.translate("ar", "Private settings"));
        assertEquals("சேமித்த பக்கங்களைப் படி (உரை மட்டும்)",
                BrowserUiText.translate("ta", "Read saved pages (offline text)"));
        assertEquals("Guardar la página actual para leer sin conexión",
                BrowserUiText.translate("es", "Save current page for offline reading"));
    }

    @Test
    public void translatesSettingsLabelsForEveryNonEnglishDashboardLocale() {
        String[] languages = {
                "ml", "hi", "ta", "te", "kn", "bn", "mr", "gu", "pa", "ur",
                "es", "fr", "ar"
        };
        for (String language : languages) {
            assertFalse("Block ads and trackers should be translated for " + language,
                    "Block ads and trackers".equals(
                            BrowserUiText.translate(language, "Block ads and trackers")));
            assertFalse("Choose search engine should be translated for " + language,
                    "Choose search engine".equals(
                            BrowserUiText.translate(language, "Choose search engine")));
        }
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
