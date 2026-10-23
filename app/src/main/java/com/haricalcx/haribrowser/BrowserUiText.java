package com.haricalcx.haribrowser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Small, offline dictionary for the native browser controls.
 * The home dashboard already has its own translations; this keeps the
 * most-used native controls consistent with the selected home-page language.
 */
final class BrowserUiText {
    private static final String[] KEYS = {
        "Search or enter an HTTPS address",
        "GO", "Back", "Forward", "New tab", "Menu", "Home", "Save",
        "History", "Find", "Settings", "Voice search", "Speak your search",
        "Family safety guide", "Voice search isn't available on this device.",
        "No speech was recognized.", "Loading stopped.", "Tabs",
        "Open tab switcher", "Tap to view site security information",
        "Search privately or enter a secure HTTPS website address"
    };
    private static final Map<String, Map<String, String>> TEXT;

    static {
        Map<String, Map<String, String>> all = new HashMap<>();
        add(all, "ml",
            "HTTPS വിലാസം നൽകുക അല്ലെങ്കിൽ തിരയുക", "പോകുക", "പിന്നോട്ട്", "മുന്നോട്ട്",
            "പുതിയ ടാബ്", "മെനു", "ഹോം", "സേവ്", "ചരിത്രം", "കണ്ടെത്തുക", "ക്രമീകരണങ്ങൾ",
            "ശബ്ദ തിരയൽ", "തിരയേണ്ടത് പറയുക", "കുടുംബ സുരക്ഷാ മാർഗ്ഗദർശിക",
            "ഈ ഉപകരണത്തിൽ ശബ്ദ തിരയൽ ലഭ്യമല്ല.", "ശബ്ദം തിരിച്ചറിയാനായില്ല.",
            "ലോഡിംഗ് നിർത്തി.", "ടാബുകൾ", "ടാബ് തിരഞ്ഞെടുക്കുക",
            "സൈറ്റ് സുരക്ഷാ വിവരം കാണാൻ തൊടുക", "സ്വകാര്യമായി തിരയുക അല്ലെങ്കിൽ സുരക്ഷിത HTTPS വിലാസം നൽകുക");
        add(all, "hi",
            "HTTPS पता लिखें या खोजें", "जाएँ", "वापस", "आगे", "नया टैब", "मेन्यू",
            "होम", "सहेजें", "इतिहास", "खोजें", "सेटिंग", "आवाज़ से खोजें",
            "अपनी खोज बोलें", "परिवार सुरक्षा मार्गदर्शिका",
            "इस डिवाइस पर आवाज़ से खोज उपलब्ध नहीं है।", "आवाज़ पहचानी नहीं गई।",
            "लोडिंग रोक दी गई।", "टैब", "टैब चुनें",
            "साइट की सुरक्षा जानकारी देखने के लिए टैप करें", "निजी रूप से खोजें या सुरक्षित HTTPS पता लिखें");
        add(all, "ta",
            "HTTPS முகவரியை உள்ளிடவும் அல்லது தேடவும்", "செல்", "பின்", "முன்",
            "புதிய தாவல்", "மெனு", "முகப்பு", "சேமி", "வரலாறு", "கண்டுபிடி", "அமைப்புகள்",
            "குரல் தேடல்", "தேடலைச் சொல்லுங்கள்", "குடும்பப் பாதுகாப்பு வழிகாட்டி",
            "இந்தச் சாதனத்தில் குரல் தேடல் இல்லை.", "குரல் அடையாளம் காணப்படவில்லை.",
            "ஏற்றுதல் நிறுத்தப்பட்டது.", "தாவல்கள்", "தாவலைத் தேர்ந்தெடுக்கவும்",
            "தளப் பாதுகாப்புத் தகவலைப் பார்க்கத் தொடவும்", "தனிப்பட்ட முறையில் தேடவும் அல்லது பாதுகாப்பான HTTPS முகவரியை உள்ளிடவும்");
        add(all, "te",
            "HTTPS చిరునామా నమోదు చేయండి లేదా శోధించండి", "వెళ్లు", "వెనుకకు", "ముందుకు",
            "కొత్త ట్యాబ్", "మెనూ", "హోమ్", "సేవ్", "చరిత్ర", "కనుగొను", "సెట్టింగ్‌లు",
            "వాయిస్ శోధన", "మీ శోధనను పలకండి", "కుటుంబ భద్రత మార్గదర్శి",
            "ఈ పరికరంలో వాయిస్ శోధన అందుబాటులో లేదు.", "మాట గుర్తించబడలేదు.",
            "లోడింగ్ ఆపబడింది.", "ట్యాబ్‌లు", "ట్యాబ్‌లను తెరవండి",
            "సైట్ భద్రతా సమాచారాన్ని చూడటానికి నొక్కండి", "ప్రైవేట్‌గా శోధించండి లేదా సురక్షిత HTTPS చిరునామా నమోదు చేయండి");
        add(all, "kn",
            "HTTPS ವಿಳಾಸ ನಮೂದಿಸಿ ಅಥವಾ ಹುಡುಕಿ", "ಹೋಗಿ", "ಹಿಂದೆ", "ಮುಂದೆ",
            "ಹೊಸ ಟ್ಯಾಬ್", "ಮೆನು", "ಮುಖಪುಟ", "ಉಳಿಸಿ", "ಇತಿಹಾಸ", "ಹುಡುಕಿ", "ಸೆಟ್ಟಿಂಗ್‌ಗಳು",
            "ಧ್ವನಿ ಹುಡುಕಾಟ", "ನಿಮ್ಮ ಹುಡುಕಾಟವನ್ನು ಹೇಳಿ", "ಕುಟುಂಬ ಸುರಕ್ಷತಾ ಮಾರ್ಗದರ್ಶಿ",
            "ಈ ಸಾಧನದಲ್ಲಿ ಧ್ವನಿ ಹುಡುಕಾಟ ಲಭ್ಯವಿಲ್ಲ.", "ಧ್ವನಿಯನ್ನು ಗುರುತಿಸಲಾಗಲಿಲ್ಲ.",
            "ಲೋಡಿಂಗ್ ನಿಲ್ಲಿಸಲಾಗಿದೆ.", "ಟ್ಯಾಬ್‌ಗಳು", "ಟ್ಯಾಬ್ ಆಯ್ಕೆಮಾಡಿ",
            "ಸೈಟ್ ಭದ್ರತಾ ಮಾಹಿತಿಗಾಗಿ ಒತ್ತಿ", "ಖಾಸಗಿಯಾಗಿ ಹುಡುಕಿ ಅಥವಾ ಸುರಕ್ಷಿತ HTTPS ವಿಳಾಸ ನಮೂದಿಸಿ");
        add(all, "bn",
            "HTTPS ঠিকানা লিখুন অথবা খুঁজুন", "যান", "পিছনে", "সামনে",
            "নতুন ট্যাব", "মেনু", "হোম", "সংরক্ষণ", "ইতিহাস", "খুঁজুন", "সেটিংস",
            "ভয়েস সার্চ", "আপনার অনুসন্ধান বলুন", "পরিবারের নিরাপত্তা নির্দেশিকা",
            "এই ডিভাইসে ভয়েস সার্চ উপলভ্য নয়।", "কোনও কথা শনাক্ত হয়নি।",
            "লোড বন্ধ করা হয়েছে।", "ট্যাব", "ট্যাব বেছে নিন",
            "সাইটের নিরাপত্তার তথ্য দেখতে ট্যাপ করুন", "ব্যক্তিগতভাবে খুঁজুন অথবা নিরাপদ HTTPS ঠিকানা লিখুন");
        add(all, "mr",
            "HTTPS पत्ता द्या किंवा शोधा", "जा", "मागे", "पुढे", "नवीन टॅब", "मेनू",
            "मुख्यपृष्ठ", "जतन करा", "इतिहास", "शोधा", "सेटिंग्ज", "आवाज शोध",
            "तुमचा शोध बोला", "कुटुंब सुरक्षा मार्गदर्शक",
            "या डिव्हाइसवर आवाज शोध उपलब्ध नाही.", "आवाज ओळखता आला नाही.",
            "लोडिंग थांबवले.", "टॅब", "टॅब निवडा",
            "साइटची सुरक्षा माहिती पाहण्यासाठी स्पर्श करा", "खाजगी शोधा किंवा सुरक्षित HTTPS पत्ता द्या");
        add(all, "gu",
            "HTTPS સરનામું દાખલ કરો અથવા શોધો", "જાઓ", "પાછળ", "આગળ",
            "નવી ટૅબ", "મેનૂ", "હોમ", "સાચવો", "ઇતિહાસ", "શોધો", "સેટિંગ્સ",
            "અવાજથી શોધ", "તમારી શોધ બોલો", "કુટુંબ સુરક્ષા માર્ગદર્શિકા",
            "આ ઉપકરણ પર અવાજથી શોધ ઉપલબ્ધ નથી.", "અવાજ ઓળખાયો નથી.",
            "લોડિંગ બંધ કર્યું.", "ટૅબ્સ", "ટૅબ પસંદ કરો",
            "સાઇટની સુરક્ષા માહિતી જોવા ટૅપ કરો", "ખાનગી રીતે શોધો અથવા સુરક્ષિત HTTPS સરનામું દાખલ કરો");
        add(all, "pa",
            "HTTPS ਪਤਾ ਦਿਓ ਜਾਂ ਖੋਜੋ", "ਜਾਓ", "ਪਿੱਛੇ", "ਅੱਗੇ",
            "ਨਵੀਂ ਟੈਬ", "ਮੀਨੂ", "ਮੁੱਖ ਪੰਨਾ", "ਸੰਭਾਲੋ", "ਇਤਿਹਾਸ", "ਲੱਭੋ", "ਸੈਟਿੰਗਾਂ",
            "ਆਵਾਜ਼ ਨਾਲ ਖੋਜ", "ਆਪਣੀ ਖੋਜ ਬੋਲੋ", "ਪਰਿਵਾਰਕ ਸੁਰੱਖਿਆ ਮਾਰਗਦਰਸ਼ਕ",
            "ਇਸ ਡਿਵਾਈਸ 'ਤੇ ਆਵਾਜ਼ ਨਾਲ ਖੋਜ ਉਪਲਬਧ ਨਹੀਂ ਹੈ।", "ਆਵਾਜ਼ ਪਛਾਣੀ ਨਹੀਂ ਗਈ।",
            "ਲੋਡਿੰਗ ਰੋਕੀ ਗਈ।", "ਟੈਬਾਂ", "ਟੈਬ ਚੁਣੋ",
            "ਸਾਈਟ ਦੀ ਸੁਰੱਖਿਆ ਜਾਣਕਾਰੀ ਲਈ ਟੈਪ ਕਰੋ", "ਨਿੱਜੀ ਤੌਰ 'ਤੇ ਖੋਜੋ ਜਾਂ ਸੁਰੱਖਿਅਤ HTTPS ਪਤਾ ਦਿਓ");
        add(all, "ur",
            "HTTPS پتہ درج کریں یا تلاش کریں", "جائیں", "پیچھے", "آگے",
            "نیا ٹیب", "مینو", "صفحۂ آغاز", "محفوظ کریں", "تاریخ", "تلاش", "ترتیبات",
            "آواز سے تلاش", "اپنی تلاش بولیں", "خاندانی تحفظ کی رہنما کتاب",
            "اس آلے پر آواز سے تلاش دستیاب نہیں ہے۔", "آواز پہچانی نہیں گئی۔",
            "لوڈنگ روک دی گئی۔", "ٹیبز", "ٹیب منتخب کریں",
            "سائٹ کی سیکیورٹی معلومات دیکھنے کے لیے دبائیں", "نجی طور پر تلاش کریں یا محفوظ HTTPS پتہ درج کریں");
        add(all, "es",
            "Busca o escribe una dirección HTTPS", "IR", "Atrás", "Adelante",
            "Nueva pestaña", "Menú", "Inicio", "Guardar", "Historial", "Buscar", "Ajustes",
            "Búsqueda por voz", "Di lo que quieres buscar", "Guía de seguridad familiar",
            "La búsqueda por voz no está disponible en este dispositivo.", "No se reconoció ninguna voz.",
            "Carga detenida.", "Pestañas", "Abrir selector de pestañas",
            "Toca para ver la seguridad del sitio", "Busca en privado o escribe una dirección HTTPS segura");
        add(all, "fr",
            "Rechercher ou saisir une adresse HTTPS", "OK", "Retour", "Suivant",
            "Nouvel onglet", "Menu", "Accueil", "Enregistrer", "Historique", "Rechercher", "Paramètres",
            "Recherche vocale", "Dictez votre recherche", "Guide de sécurité familiale",
            "La recherche vocale n'est pas disponible sur cet appareil.", "Aucune parole reconnue.",
            "Chargement arrêté.", "Onglets", "Ouvrir le sélecteur d’onglets",
            "Touchez pour afficher la sécurité du site", "Recherchez en privé ou saisissez une adresse HTTPS sécurisée");
        add(all, "ar",
            "ابحث أو أدخل عنوان HTTPS", "اذهب", "رجوع", "تقدم",
            "علامة تبويب جديدة", "القائمة", "الرئيسية", "حفظ", "السجل", "بحث", "الإعدادات",
            "البحث الصوتي", "انطق ما تريد البحث عنه", "دليل أمان الأسرة",
            "البحث الصوتي غير متاح على هذا الجهاز.", "لم يتم التعرف على الكلام.",
            "تم إيقاف التحميل.", "علامات التبويب", "فتح محدد علامات التبويب",
            "اضغط لعرض معلومات أمان الموقع", "ابحث بخصوصية أو أدخل عنوان HTTPS آمنًا");
        TEXT = Collections.unmodifiableMap(all);
    }

    private BrowserUiText() {}

    private static void add(Map<String, Map<String, String>> all, String language, String... values) {
        Map<String, String> translated = new HashMap<>();
        int count = Math.min(KEYS.length, values.length);
        for (int i = 0; i < count; i++) translated.put(KEYS[i], values[i]);
        all.put(language, Collections.unmodifiableMap(translated));
    }

    static String translate(String languageCode, String english) {
        if (english == null) return "";
        String language = languageCode == null ? "en" :
                languageCode.trim().toLowerCase(Locale.ROOT);
        int separator = language.indexOf('-');
        if (separator >= 0) language = language.substring(0, separator);
        Map<String, String> translated = TEXT.get(language);
        if (translated == null) return english;
        String result = translated.get(english);
        return result == null ? english : result;
    }

    static String speechLocale(String languageCode) {
        if (languageCode == null) return "en-IN";
        switch (languageCode.toLowerCase(Locale.ROOT)) {
            case "ml": return "ml-IN";
            case "hi": return "hi-IN";
            case "ta": return "ta-IN";
            case "te": return "te-IN";
            case "kn": return "kn-IN";
            case "bn": return "bn-IN";
            case "mr": return "mr-IN";
            case "gu": return "gu-IN";
            case "pa": return "pa-IN";
            case "ur": return "ur-IN";
            case "es": return "es-ES";
            case "fr": return "fr-FR";
            case "ar": return "ar-SA";
            default: return "en-IN";
        }
    }
}
