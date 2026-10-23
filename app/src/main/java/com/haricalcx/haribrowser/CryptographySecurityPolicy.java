package com.haricalcx.haribrowser;

import java.net.URI;
import java.net.URISyntaxException;

/** Pure-Java allowlist and metadata sanitizers for the local cryptography screen. */
final class CryptographySecurityPolicy {
    static final String ASSET_HOST = "appassets.androidplatform.net";
    static final String ASSET_PATH = "/assets/hari_cryptography.html";
    static final String ASSET_URL = "https://" + ASSET_HOST + ASSET_PATH;

    private CryptographySecurityPolicy() {}

    static boolean isAllowedAssetUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && ASSET_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getUserInfo() == null
                    && ASSET_PATH.equals(uri.getRawPath())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    static String safeFileName(String value) {
        String input = value == null ? "" : value;
        StringBuilder out = new StringBuilder(Math.min(input.length(), 180));
        for (int i = 0; i < input.length() && out.length() < 180; i++) {
            char c = input.charAt(i);
            if (c < 0x20 || c == 0x7f || c == '/' || c == '\\'
                    || c == ':' || c == '*' || c == '?' || c == '"'
                    || c == '<' || c == '>' || c == '|') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        String clean = out.toString().trim();
        if (clean.isEmpty() || ".".equals(clean) || "..".equals(clean)) {
            return "hari-output.bin";
        }
        return clean;
    }

    static String safeMimeType(String value) {
        if (value == null) return "application/octet-stream";
        int parameterStart = value.indexOf(';');
        String base = (parameterStart >= 0 ? value.substring(0, parameterStart) : value).trim();
        return base.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
                ? base : "application/octet-stream";
    }
}