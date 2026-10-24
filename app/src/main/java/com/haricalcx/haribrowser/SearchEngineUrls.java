package com.haricalcx.haribrowser;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/** Centralized HTTPS search URLs for engines exposed by the browser picker. */
final class SearchEngineUrls {
    private SearchEngineUrls() {}

    static String searchUrl(String engine, String query) {
        String base;
        switch (engine == null ? "" : engine) {
            case "Startpage":
                base = "https://www.startpage.com/sp/search?query=";
                break;
            case "Brave Search":
                base = "https://search.brave.com/search?q=";
                break;
            case "Google":
                base = "https://www.google.co.in/search?q=";
                break;
            case "Bing":
                base = "https://www.bing.com/search?q=";
                break;
            case "Ecosia":
                base = "https://www.ecosia.org/search?q=";
                break;
            case "Mojeek":
                base = "https://www.mojeek.com/search?q=";
                break;
            case "Qwant":
                base = "https://www.qwant.com/?q=";
                break;
            case "DuckDuckGo":
            default:
                base = "https://duckduckgo.com/?q=";
                break;
        }

        String encoded;
        try {
            encoded = URLEncoder.encode(query == null ? "" : query, "UTF-8");
        } catch (UnsupportedEncodingException impossibleOnAndroid) {
            // UTF-8 is guaranteed by the Android runtime; fail safely if that contract breaks.
            encoded = "";
        }
        return base + encoded;
    }
}
