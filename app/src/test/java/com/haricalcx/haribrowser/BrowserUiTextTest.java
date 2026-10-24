package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
    public void translatesBookmarkFolderControlsForIndianLanguages() {
        assertEquals("ഫോൾഡറുകൾ", BrowserUiText.translate("ml", "Folders"));
        assertEquals("फ़ोल्डर", BrowserUiText.translate("hi", "Folders"));
        assertEquals("கோப்புறைகள்", BrowserUiText.translate("ta", "Folders"));
        assertEquals("Folders", BrowserUiText.translate("en", "Folders"));
        assertEquals("புத்தகக்குறி கோப்புறையைத் திறக்கவும்",
                BrowserUiText.translate("ta", "Open bookmarks folder"));
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

    @Test
    public void translatesTlsCertificateWarningForSupportedNonEnglishLocales() {
        String warning = "Blocked: the site's security certificate is invalid.";
        assertEquals(warning, BrowserUiText.translate("en", warning));
        assertEquals("തടഞ്ഞു: സൈറ്റിന്റെ സുരക്ഷാ സർട്ടിഫിക്കറ്റ് അസാധുവാണ്.",
                BrowserUiText.translate("ml", warning));
        assertEquals("अवरुद्ध: साइट का सुरक्षा प्रमाणपत्र अमान्य है।",
                BrowserUiText.translate("hi-IN", warning));
        assertEquals("தடுக்கப்பட்டது: தளத்தின் பாதுகாப்புச் சான்றிதழ் செல்லாது.",
                BrowserUiText.translate("ta", warning));
        assertEquals("تم الحظر: شهادة أمان الموقع غير صالحة.",
                BrowserUiText.translate("ar", warning));

        for (String language : new String[] {
                "ml", "hi", "ta", "te", "kn", "bn", "mr", "gu", "pa", "ur", "es", "fr", "ar"
        }) {
            assertFalse("TLS warning should be localized for " + language,
                    warning.equals(BrowserUiText.translate(language, warning)));
        }
    }

}
