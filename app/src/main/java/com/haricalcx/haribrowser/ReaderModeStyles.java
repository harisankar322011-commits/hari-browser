package com.haricalcx.haribrowser;

import org.json.JSONObject;

/**
 * Reader-style CSS and injection script for the current WebView document.
 * Uses platform sans-serif font fallback so Indic scripts use installed system
 * glyphs, preserves the source page content, and never rewrites it as HTML.
 */
final class ReaderModeStyles {
    static final String CSS =
            "html{background:#fff!important;color:#172033!important}" +
            "body{box-sizing:border-box!important;width:auto!important;max-width:780px!important;" +
            "margin:0 auto!important;padding:clamp(16px,4vw,28px)!important;" +
            "font-family:system-ui,-apple-system,BlinkMacSystemFont,sans-serif!important;" +
            "font-size:19px!important;line-height:1.8!important;overflow-wrap:break-word!important;" +
            "word-break:normal!important;background:#fff!important;color:#172033!important}" +
            "body *{font-family:inherit!important;line-height:inherit!important}" +
            "p,li,blockquote,pre,td,th{margin-top:.75em!important;margin-bottom:.75em!important}" +
            "img,video,iframe{max-width:100%!important;height:auto!important}" +
            "nav,aside,footer,[role=navigation],.advertisement,.advert,.ads,.ad-slot,.social-share," +
            ".cookie-banner,.newsletter-signup{max-width:100%!important}";

    private ReaderModeStyles() {}

    static String documentStyleScript() {
        return "(function(){var old=document.getElementById('hari-reader-style');" +
                "if(old)old.remove();var s=document.createElement('style');" +
                "s.id='hari-reader-style';s.textContent=" + JSONObject.quote(CSS) + ";" +
                "(document.head||document.documentElement).appendChild(s);})();";
    }
}
