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
    private static final String[] EXTRA_KEYS = {
        "Block ads and trackers",
        "JavaScript (compatibility / risk)",
        "Desktop website mode",
        "Allow first-party cookies",
        "Save browsing history",
        "Toggle reader-style view",
        "Clear cookies, cache, site storage and history",
        "Open downloads",
        "Bookmarks",
        "About privacy and security",
        "Open private browsing",
        "Choose search engine",
        "Load website images",
        "Choose page text size",
        "Site security details",
        "Choose home page language",
        "Hari AI · API settings",
        "Teen protection",
        "Security & privacy scan",
        "Site permission controls",
        "Hari Antivirus · scan a file and manage quarantine",
        "Lite mode · block images and known trackers",
        "Save current page for offline reading",
        "Read saved pages (offline text)"
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
        addExtras(all, "ml",
            "പരസ്യങ്ങളും ട്രാക്കറുകളും തടയുക", "JavaScript (അനുയോജ്യത / അപകടസാധ്യത)",
            "ഡെസ്ക്ടോപ്പ് വെബ്സൈറ്റ് മോഡ്", "ആദ്യകക്ഷി കുക്കികൾ അനുവദിക്കുക", "ബ്രൗസിംഗ് ചരിത്രം സൂക്ഷിക്കുക",
            "വായനാ കാഴ്ച മാറ്റുക", "കുക്കികൾ, കാഷെ, സൈറ്റ് ഡാറ്റ, ചരിത്രം മായ്ക്കുക", "ഡൗൺലോഡുകൾ തുറക്കുക",
            "ബുക്ക്മാർക്കുകൾ", "സ്വകാര്യതയും സുരക്ഷയും", "സ്വകാര്യ ബ്രൗസിംഗ് തുറക്കുക", "തിരയൽ എഞ്ചിൻ തിരഞ്ഞെടുക്കുക",
            "വെബ്സൈറ്റ് ചിത്രങ്ങൾ ലോഡ് ചെയ്യുക", "പേജിലെ അക്ഷര വലുപ്പം തിരഞ്ഞെടുക്കുക", "സൈറ്റ് സുരക്ഷാ വിവരങ്ങൾ",
            "ഹോം പേജിന്റെ ഭാഷ തിരഞ്ഞെടുക്കുക", "Hari AI · API ക്രമീകരണങ്ങൾ", "ടീൻ സംരക്ഷണം",
            "സുരക്ഷയും സ്വകാര്യതയും പരിശോധിക്കുക", "സൈറ്റ് അനുമതികൾ", "Hari Antivirus · ഫയൽ പരിശോധിക്കുകയും ക്വാറന്റൈൻ നിയന്ത്രിക്കുകയും ചെയ്യുക",
            "Lite മോഡ് · ചിത്രങ്ങളും അറിയപ്പെടുന്ന ട്രാക്കറുകളും തടയുക", "ഓഫ്‌ലൈനിൽ വായിക്കാൻ ഇപ്പോഴത്തെ പേജ് സേവ് ചെയ്യുക",
            "സേവ് ചെയ്ത പേജുകൾ വായിക്കുക (ടെക്സ്റ്റ് മാത്രം)");
        addExtras(all, "hi",
            "विज्ञापन और ट्रैकर रोकें", "JavaScript (अनुकूलता / जोखिम)", "डेस्कटॉप वेबसाइट मोड",
            "पहले पक्ष की कुकीज़ की अनुमति दें", "ब्राउज़िंग इतिहास सहेजें", "रीडर दृश्य बदलें",
            "कुकीज़, कैश, साइट डेटा और इतिहास साफ़ करें", "डाउनलोड खोलें", "बुकमार्क",
            "गोपनीयता और सुरक्षा के बारे में", "निजी ब्राउज़िंग खोलें", "खोज इंजन चुनें", "वेबसाइट चित्र लोड करें",
            "पृष्ठ का टेक्स्ट आकार चुनें", "साइट सुरक्षा विवरण", "होम पेज की भाषा चुनें", "Hari AI · API सेटिंग",
            "किशोर सुरक्षा", "सुरक्षा और गोपनीयता जाँच", "साइट अनुमतियाँ",
            "Hari Antivirus · फ़ाइल स्कैन करें और क्वारंटीन प्रबंधित करें", "Lite मोड · चित्र और ज्ञात ट्रैकर रोकें",
            "ऑफ़लाइन पढ़ने के लिए वर्तमान पेज सहेजें", "सहेजे गए पेज पढ़ें (केवल टेक्स्ट)");
        addExtras(all, "ta",
            "விளம்பரங்கள் மற்றும் டிராக்கர்களைத் தடு", "JavaScript (இணக்கத்தன்மை / ஆபத்து)", "டெஸ்க்டாப் தள முறை",
            "முதல் தரப்பு குக்கீகளை அனுமதி", "உலாவல் வரலாற்றைச் சேமி", "வாசிப்பு காட்சியை மாற்று",
            "குக்கீகள், கேச், தளத் தரவு மற்றும் வரலாற்றை அழி", "பதிவிறக்கங்களைத் திற", "புத்தகக்குறிகள்",
            "தனியுரிமை மற்றும் பாதுகாப்பு பற்றி", "தனிப்பட்ட உலாவலைத் திற", "தேடுபொறியைத் தேர்வு செய்",
            "இணையதளப் படங்களை ஏற்று", "பக்க எழுத்து அளவைத் தேர்வு செய்", "தளப் பாதுகாப்பு விவரங்கள்",
            "முகப்புப் பக்க மொழியைத் தேர்வு செய்", "Hari AI · API அமைப்புகள்", "இளையோர் பாதுகாப்பு",
            "பாதுகாப்பு மற்றும் தனியுரிமை சோதனை", "தள அனுமதி கட்டுப்பாடுகள்",
            "Hari Antivirus · கோப்பைச் சோதித்து தனிமைப்படுத்தலை நிர்வகி", "Lite முறை · படங்கள் மற்றும் தெரிந்த டிராக்கர்களைத் தடு",
            "ஆஃப்லைனில் படிக்க தற்போதைய பக்கத்தைச் சேமி", "சேமித்த பக்கங்களைப் படி (உரை மட்டும்)");
        addExtras(all, "te",
            "ప్రకటనలు మరియు ట్రాకర్లను నిరోధించండి", "JavaScript (అనుకూలత / ప్రమాదం)", "డెస్క్‌టాప్ వెబ్‌సైట్ మోడ్",
            "ఫస్ట్-పార్టీ కుకీలను అనుమతించండి", "బ్రౌజింగ్ చరిత్రను సేవ్ చేయండి", "రీడర్ వీక్షణను మార్చండి",
            "కుకీలు, క్యాష్, సైట్ డేటా, చరిత్రను క్లియర్ చేయండి", "డౌన్‌లోడ్‌లు తెరవండి", "బుక్‌మార్క్‌లు",
            "గోప్యత మరియు భద్రత గురించి", "ప్రైవేట్ బ్రౌజింగ్ తెరవండి", "శోధన ఇంజిన్ ఎంచుకోండి",
            "వెబ్‌సైట్ చిత్రాలను లోడ్ చేయండి", "పేజీ అక్షర పరిమాణం ఎంచుకోండి", "సైట్ భద్రత వివరాలు",
            "హోమ్ పేజీ భాష ఎంచుకోండి", "Hari AI · API సెట్టింగ్‌లు", "టీనేజ్ రక్షణ",
            "భద్రత మరియు గోప్యత స్కాన్", "సైట్ అనుమతి నియంత్రణలు",
            "Hari Antivirus · ఫైల్ స్కాన్ చేసి క్వారంటైన్ నిర్వహించండి", "Lite మోడ్ · చిత్రాలు మరియు తెలిసిన ట్రాకర్లను నిరోధించండి",
            "ఆఫ్‌లైన్‌లో చదవడానికి ప్రస్తుత పేజీని సేవ్ చేయండి", "సేవ్ చేసిన పేజీలు చదవండి (టెక్స్ట్ మాత్రమే)");
        addExtras(all, "kn",
            "ಜಾಹೀರಾತು ಮತ್ತು ಟ್ರ್ಯಾಕರ್‌ಗಳನ್ನು ತಡೆಹಿಡಿ", "JavaScript (ಹೊಂದಾಣಿಕೆ / ಅಪಾಯ)", "ಡೆಸ್ಕ್‌ಟಾಪ್ ವೆಬ್‌ಸೈಟ್ ಮೋಡ್",
            "ಪ್ರಥಮ-ಪಕ್ಷ ಕುಕೀಗಳನ್ನು ಅನುಮತಿಸಿ", "ಬ್ರೌಸಿಂಗ್ ಇತಿಹಾಸ ಉಳಿಸಿ", "ರೀಡರ್ ವೀಕ್ಷಣೆ ಬದಲಿಸಿ",
            "ಕುಕೀಗಳು, ಕ್ಯಾಶ್, ಸೈಟ್ ಡೇಟಾ ಮತ್ತು ಇತಿಹಾಸ ಅಳಿಸಿ", "ಡೌನ್‌ಲೋಡ್ ತೆರೆಯಿರಿ", "ಬುಕ್‌ಮಾರ್ಕ್‌ಗಳು",
            "ಗೌಪ್ಯತೆ ಮತ್ತು ಭದ್ರತೆ ಕುರಿತು", "ಖಾಸಗಿ ಬ್ರೌಸಿಂಗ್ ತೆರೆಯಿರಿ", "ಹುಡುಕಾಟ ಎಂಜಿನ್ ಆಯ್ಕೆಮಾಡಿ",
            "ವೆಬ್‌ಸೈಟ್ ಚಿತ್ರಗಳನ್ನು ಲೋಡ್ ಮಾಡಿ", "ಪುಟದ ಪಠ್ಯ ಗಾತ್ರ ಆಯ್ಕೆಮಾಡಿ", "ಸೈಟ್ ಭದ್ರತಾ ವಿವರಗಳು",
            "ಮುಖಪುಟದ ಭಾಷೆ ಆಯ್ಕೆಮಾಡಿ", "Hari AI · API ಸೆಟ್ಟಿಂಗ್‌ಗಳು", "ಟೀನ್ ರಕ್ಷಣೆ",
            "ಭದ್ರತೆ ಮತ್ತು ಗೌಪ್ಯತೆ ಪರಿಶೀಲನೆ", "ಸೈಟ್ ಅನುಮತಿ ನಿಯಂತ್ರಣಗಳು",
            "Hari Antivirus · ಫೈಲ್ ಪರಿಶೀಲಿಸಿ ಮತ್ತು ಕ್ವಾರಂಟೈನ್ ನಿರ್ವಹಿಸಿ", "Lite ಮೋಡ್ · ಚಿತ್ರಗಳು ಮತ್ತು ತಿಳಿದಿರುವ ಟ್ರ್ಯಾಕರ್‌ಗಳನ್ನು ತಡೆಹಿಡಿ",
            "ಆಫ್‌ಲೈನ್ ಓದಲು ಪ್ರಸ್ತುತ ಪುಟವನ್ನು ಉಳಿಸಿ", "ಉಳಿಸಿದ ಪುಟಗಳನ್ನು ಓದಿ (ಪಠ್ಯ ಮಾತ್ರ)");
        addExtras(all, "bn",
            "বিজ্ঞাপন ও ট্র্যাকার আটকান", "JavaScript (সামঞ্জস্য / ঝুঁকি)", "ডেস্কটপ ওয়েবসাইট মোড",
            "প্রথম-পক্ষের কুকি অনুমতি দিন", "ব্রাউজিং ইতিহাস সংরক্ষণ করুন", "রিডার ভিউ বদলান",
            "কুকি, ক্যাশে, সাইট ডেটা ও ইতিহাস মুছুন", "ডাউনলোড খুলুন", "বুকমার্ক",
            "গোপনীয়তা ও নিরাপত্তা সম্পর্কে", "ব্যক্তিগত ব্রাউজিং খুলুন", "সার্চ ইঞ্জিন বেছে নিন",
            "ওয়েবসাইটের ছবি লোড করুন", "পৃষ্ঠার লেখার আকার বেছে নিন", "সাইট নিরাপত্তার বিবরণ",
            "হোম পেজের ভাষা বেছে নিন", "Hari AI · API সেটিংস", "কিশোর সুরক্ষা",
            "নিরাপত্তা ও গোপনীয়তা পরীক্ষা", "সাইটের অনুমতি নিয়ন্ত্রণ",
            "Hari Antivirus · ফাইল পরীক্ষা ও কোয়ারেন্টাইন পরিচালনা", "Lite মোড · ছবি ও পরিচিত ট্র্যাকার আটকান",
            "অফলাইনে পড়তে বর্তমান পৃষ্ঠা সংরক্ষণ করুন", "সংরক্ষিত পৃষ্ঠা পড়ুন (শুধু লেখা)");
        addExtras(all, "mr",
            "जाहिराती आणि ट्रॅकर्स अवरोधित करा", "JavaScript (सुसंगतता / धोका)", "डेस्कटॉप वेबसाइट मोड",
            "फर्स्ट-पार्टी कुकीजना परवानगी द्या", "ब्राउझिंग इतिहास जतन करा", "रीडर दृश्य बदला",
            "कुकीज, कॅशे, साइट डेटा आणि इतिहास साफ करा", "डाउनलोड उघडा", "बुकमार्क",
            "गोपनीयता आणि सुरक्षिततेबद्दल", "खाजगी ब्राउझिंग उघडा", "शोध इंजिन निवडा",
            "वेबसाइट प्रतिमा लोड करा", "पृष्ठाचा मजकूर आकार निवडा", "साइट सुरक्षितता तपशील",
            "मुख्यपृष्ठाची भाषा निवडा", "Hari AI · API सेटिंग्ज", "किशोर संरक्षण",
            "सुरक्षितता आणि गोपनीयता तपासणी", "साइट परवानगी नियंत्रण",
            "Hari Antivirus · फाइल तपासा आणि क्वारंटाइन व्यवस्थापित करा", "Lite मोड · प्रतिमा आणि ज्ञात ट्रॅकर्स अवरोधित करा",
            "ऑफलाइन वाचण्यासाठी सध्याचे पृष्ठ जतन करा", "जतन केलेली पाने वाचा (फक्त मजकूर)");
        addExtras(all, "gu",
            "જાહેરાતો અને ટ્રેકર્સ રોકો", "JavaScript (સુસંગતતા / જોખમ)", "ડેસ્કટોપ વેબસાઇટ મોડ",
            "પ્રથમ-પક્ષ કૂકીઝને મંજૂરી આપો", "બ્રાઉઝિંગ ઇતિહાસ સાચવો", "રીડર દૃશ્ય બદલો",
            "કૂકીઝ, કૅશ, સાઇટ ડેટા અને ઇતિહાસ સાફ કરો", "ડાઉનલોડ્સ ખોલો", "બુકમાર્ક્સ",
            "ગોપનીયતા અને સુરક્ષા વિશે", "ખાનગી બ્રાઉઝિંગ ખોલો", "શોધ એન્જિન પસંદ કરો",
            "વેબસાઇટની છબીઓ લોડ કરો", "પાનાનો લખાણ કદ પસંદ કરો", "સાઇટ સુરક્ષા વિગતો",
            "હોમ પેજની ભાષા પસંદ કરો", "Hari AI · API સેટિંગ્સ", "કિશોર સુરક્ષા",
            "સુરક્ષા અને ગોપનીયતા તપાસ", "સાઇટ પરવાનગી નિયંત્રણો",
            "Hari Antivirus · ફાઇલ તપાસો અને ક્વોરન્ટાઇન સંભાળો", "Lite મોડ · છબીઓ અને જાણીતા ટ્રેકર્સ રોકો",
            "ઑફલાઇન વાંચવા માટે વર્તમાન પાનું સાચવો", "સાચવેલા પાનાં વાંચો (ફક્ત લખાણ)");
        addExtras(all, "pa",
            "ਇਸ਼ਤਿਹਾਰ ਅਤੇ ਟ੍ਰੈਕਰ ਰੋਕੋ", "JavaScript (ਅਨੁਕੂਲਤਾ / ਜੋਖਮ)", "ਡੈਸਕਟਾਪ ਵੈੱਬਸਾਈਟ ਮੋਡ",
            "ਪਹਿਲੀ-ਧਿਰ ਕੂਕੀਜ਼ ਦੀ ਇਜਾਜ਼ਤ ਦਿਓ", "ਬ੍ਰਾਊਜ਼ਿੰਗ ਇਤਿਹਾਸ ਸੰਭਾਲੋ", "ਰੀਡਰ ਦ੍ਰਿਸ਼ ਬਦਲੋ",
            "ਕੂਕੀਜ਼, ਕੈਸ਼, ਸਾਈਟ ਡਾਟਾ ਅਤੇ ਇਤਿਹਾਸ ਸਾਫ਼ ਕਰੋ", "ਡਾਊਨਲੋਡ ਖੋਲ੍ਹੋ", "ਬੁੱਕਮਾਰਕ",
            "ਪਰਦੇਦਾਰੀ ਅਤੇ ਸੁਰੱਖਿਆ ਬਾਰੇ", "ਨਿੱਜੀ ਬ੍ਰਾਊਜ਼ਿੰਗ ਖੋਲ੍ਹੋ", "ਖੋਜ ਇੰਜਣ ਚੁਣੋ",
            "ਵੈੱਬਸਾਈਟ ਤਸਵੀਰਾਂ ਲੋਡ ਕਰੋ", "ਪੰਨੇ ਦਾ ਲਿਖਤ ਆਕਾਰ ਚੁਣੋ", "ਸਾਈਟ ਸੁਰੱਖਿਆ ਵੇਰਵੇ",
            "ਹੋਮ ਪੰਨੇ ਦੀ ਭਾਸ਼ਾ ਚੁਣੋ", "Hari AI · API ਸੈਟਿੰਗਾਂ", "ਕਿਸ਼ੋਰ ਸੁਰੱਖਿਆ",
            "ਸੁਰੱਖਿਆ ਅਤੇ ਪਰਦੇਦਾਰੀ ਜਾਂਚ", "ਸਾਈਟ ਇਜਾਜ਼ਤ ਕੰਟਰੋਲ",
            "Hari Antivirus · ਫਾਈਲ ਜਾਂਚੋ ਅਤੇ ਕੁਆਰੰਟੀਨ ਸੰਭਾਲੋ", "Lite ਮੋਡ · ਤਸਵੀਰਾਂ ਅਤੇ ਜਾਣੇ-ਪਛਾਣੇ ਟ੍ਰੈਕਰ ਰੋਕੋ",
            "ਆਫਲਾਈਨ ਪੜ੍ਹਨ ਲਈ ਮੌਜੂਦਾ ਪੰਨਾ ਸੰਭਾਲੋ", "ਸੰਭਾਲੇ ਪੰਨੇ ਪੜ੍ਹੋ (ਕੇਵਲ ਲਿਖਤ)");
        addExtras(all, "ur",
            "اشتہارات اور ٹریکرز روکیں", "JavaScript (مطابقت / خطرہ)", "ڈیسک ٹاپ ویب سائٹ موڈ",
            "فرسٹ پارٹی کوکیز کی اجازت دیں", "براؤزنگ کی تاریخ محفوظ کریں", "ریڈر منظر تبدیل کریں",
            "کوکیز، کیش، سائٹ ڈیٹا اور تاریخ صاف کریں", "ڈاؤن لوڈز کھولیں", "بک مارکس",
            "رازداری اور سیکیورٹی کے بارے میں", "نجی براؤزنگ کھولیں", "سرچ انجن منتخب کریں",
            "ویب سائٹ کی تصاویر لوڈ کریں", "صفحے کے متن کا سائز منتخب کریں", "سائٹ سیکیورٹی کی تفصیل",
            "ہوم پیج کی زبان منتخب کریں", "Hari AI · API ترتیبات", "نوعمر تحفظ",
            "سیکیورٹی اور رازداری کی جانچ", "سائٹ اجازتوں کا کنٹرول",
            "Hari Antivirus · فائل اسکین اور قرنطینہ کا انتظام", "Lite موڈ · تصاویر اور معلوم ٹریکرز روکیں",
            "آف لائن پڑھنے کے لیے موجودہ صفحہ محفوظ کریں", "محفوظ صفحات پڑھیں (صرف متن)");
        addExtras(all, "es",
            "Bloquear anuncios y rastreadores", "JavaScript (compatibilidad / riesgo)", "Modo de escritorio",
            "Permitir cookies propias", "Guardar historial", "Alternar vista de lectura",
            "Borrar cookies, caché, datos del sitio e historial", "Abrir descargas", "Marcadores",
            "Privacidad y seguridad", "Abrir navegación privada", "Elegir buscador",
            "Cargar imágenes de sitios", "Elegir tamaño del texto", "Detalles de seguridad del sitio",
            "Elegir idioma de inicio", "Hari AI · ajustes de API", "Protección para adolescentes",
            "Análisis de seguridad y privacidad", "Controles de permisos del sitio",
            "Hari Antivirus · analizar archivo y gestionar cuarentena", "Modo ligero · bloquear imágenes y rastreadores conocidos",
            "Guardar la página actual para leer sin conexión", "Leer páginas guardadas (solo texto)");
        addExtras(all, "fr",
            "Bloquer pubs et traceurs", "JavaScript (compatibilité / risque)", "Mode site pour ordinateur",
            "Autoriser les cookies internes", "Enregistrer l’historique", "Activer la vue lecture",
            "Effacer cookies, cache, données des sites et historique", "Ouvrir les téléchargements", "Favoris",
            "Confidentialité et sécurité", "Ouvrir la navigation privée", "Choisir le moteur de recherche",
            "Charger les images des sites", "Choisir la taille du texte", "Détails de sécurité du site",
            "Choisir la langue d’accueil", "Hari AI · paramètres API", "Protection des adolescents",
            "Analyse de sécurité et de confidentialité", "Contrôle des autorisations des sites",
            "Hari Antivirus · analyser un fichier et gérer la quarantaine", "Mode léger · bloquer images et traceurs connus",
            "Enregistrer la page pour lecture hors connexion", "Lire les pages enregistrées (texte uniquement)");
        addExtras(all, "ar",
            "حظر الإعلانات وأدوات التتبع", "JavaScript (التوافق / المخاطر)", "وضع موقع سطح المكتب",
            "السماح بملفات تعريف الارتباط للطرف الأول", "حفظ سجل التصفح", "تبديل وضع القراءة",
            "مسح ملفات تعريف الارتباط وذاكرة التخزين وبيانات المواقع والسجل", "فتح التنزيلات", "الإشارات المرجعية",
            "حول الخصوصية والأمان", "فتح التصفح الخاص", "اختيار محرك البحث",
            "تحميل صور المواقع", "اختيار حجم نص الصفحة", "تفاصيل أمان الموقع",
            "اختيار لغة الصفحة الرئيسية", "Hari AI · إعدادات API", "حماية المراهقين",
            "فحص الأمان والخصوصية", "عناصر التحكم في أذونات الموقع",
            "Hari Antivirus · فحص ملف وإدارة العزل", "الوضع الخفيف · حظر الصور وأدوات التتبع المعروفة",
            "حفظ الصفحة الحالية للقراءة دون اتصال", "قراءة الصفحات المحفوظة (نص فقط)");

        TEXT = Collections.unmodifiableMap(all);
    }

    private BrowserUiText() {}

    private static void add(Map<String, Map<String, String>> all, String language, String... values) {
        Map<String, String> translated = new HashMap<>();
        int count = Math.min(KEYS.length, values.length);
        for (int i = 0; i < count; i++) translated.put(KEYS[i], values[i]);
        all.put(language, Collections.unmodifiableMap(translated));
    }


    private static void addExtras(Map<String, Map<String, String>> all, String language, String... values) {
        if (values.length != EXTRA_KEYS.length) {
            throw new IllegalArgumentException("Incorrect translation count for " + language
                    + ": " + values.length + " expected " + EXTRA_KEYS.length);
        }
        Map<String, String> translated = new HashMap<>(all.getOrDefault(language, Collections.emptyMap()));
        for (int i = 0; i < EXTRA_KEYS.length; i++) {
            translated.put(EXTRA_KEYS[i], values[i]);
        }
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
