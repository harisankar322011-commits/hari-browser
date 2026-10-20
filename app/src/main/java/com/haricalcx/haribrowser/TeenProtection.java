package com.haricalcx.haribrowser;

import android.content.SharedPreferences;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local, opt-in teen search protection. This is a limited browser safeguard,
 * not a complete content filter or a device-wide parental-control service.
 */
final class TeenProtection {
    static final String ENABLED_KEY = "teen_protection_enabled";
    static final String PIN_SALT_KEY = "teen_protection_pin_salt";
    static final String PIN_HASH_KEY = "teen_protection_pin_hash";
    static final String PIN_FAILURES_KEY = "teen_protection_pin_failures";
    static final String PIN_LOCK_UNTIL_KEY = "teen_protection_pin_lock_until";
    private static final int PIN_ITERATIONS = 120_000;
    private static final int PIN_BITS = 256;

    private static final String[] BLOCKED_PHRASES = {
        "adult video", "adult videos", "sex video", "sex videos", "sex chat",
        "nude photos", "nude pictures", "naked photos", "naked pictures",
        "leaked nudes", "onlyfans leak", "onlyfans leaks", "explicit video",
        "explicit videos", "explicit images", "erotic video", "erotic videos",
        "adult content sites", "adult websites", "adult website", "adult sites",
        "cam girl sites", "webcam girls", "cam girls", "nude girls", "naked girls",
        "naked women", "naked pictures", "naked pics", "sex cam", "sex cams",
        "webcam sex", "sex tape", "sex tapes", "xxx videos", "xxx photos",
        "explicit photos", "free porn", "porn video", "porn videos", "porn site",
        "porn sites", "watch porn", "porn images", "porn clips",
        "online casino", "online casinos", "casino online", "casino games",
        "casino games for money", "casino sites", "best casinos", "real money casino",
        "real money gambling", "online gambling", "gambling sites", "sports betting",
        "betting sites", "betting apps", "best betting apps", "poker for money",
        "real money poker", "slot machines online", "online slots", "slots for money",
        "bet now", "sportsbook", "sportsbooks", "bookmaker sites",
        "पोर्न", "पोर्नोग्राफी", "अश्लील वीडियो", "अश्लील तस्वीरें",
        "ஆபாச வீடியோ", "అశ్లీల వీడియో", "ಅಶ್ಲೀಲ ವೀಡಿಯೊ",
        "അശ്ലീലം", "നീലച്ചിത്രം", "pornografía", "pornographie", "إباحي"
    };

    // Single terms are matched after punctuation is compacted, so p.o.r.n is not a bypass.
    private static final String[] GENERIC_BLOCKED_TERMS = {
        "porn", "pornography", "pornographic", "hentai", "nsfw", "xxx", "nudes"
    };

    // Known sites are a separate, high-confidence rule and do not get a support-query exception.
    private static final String[] BLOCKED_COMPACT_MARKERS = {
        "pornhub", "xvideos", "xhamster", "xnxx", "redtube", "youporn", "tube8",
        "spankbang", "eporner", "hclips", "pornpics", "fapello", "erome",
        "chaturbate", "stripchat", "livejasmin", "rule34", "nhentai",
        "brazzers", "bangbros", "onlyfans", "xxxvideos"
    };

    private static final String[] SAFE_SUPPORT_PHRASES = {
        "porn addiction", "stop watching porn", "help with porn", "porn recovery",
        "recover from pornography", "someone leaked my nudes", "report leaked nudes",
        "what to do if someone shared my nudes", "sextortion help",
        "gambling addiction", "problem gambling help", "help with gambling",
        "stop gambling", "gambling support", "online gambling addiction"
    };

    private static final String[] SUPPORT_SENSITIVE_TERMS = {
        "porn", "pornography", "pornographic", "nudes", "leaked nudes",
        "online gambling", "real money gambling", "gambling sites"
    };

    private static final String[] BLOCKED_DOMAINS = {
        "porn.com", "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com",
        "redtube.com", "youporn.com", "tube8.com", "spankbang.com", "eporner.com",
        "hclips.com", "pornpics.com", "fapello.com", "erome.com", "chaturbate.com",
        "stripchat.com", "cam4.com", "livejasmin.com", "rule34.xxx", "nhentai.net",
        "brazzers.com", "bangbros.com", "onlyfans.com",
        "bet365.com", "stake.com", "betway.com", "1xbet.com", "betfair.com",
        "pokerstars.com", "888casino.com", "draftkings.com", "fanduel.com",
        "roobet.com", "bc.game", "parimatch.com", "williamhill.com",
        "unibet.com", "casino.com", "betano.com"
    };


    private TeenProtection() {}

    static boolean isEnabled(SharedPreferences preferences) {
        return preferences != null && preferences.getBoolean(ENABLED_KEY, false);
    }

    static boolean containsBlockedSearchTerm(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return false;
        String padded = " " + normalized + " ";
        String compact = foldLeetspeak(normalized.replace(" ", ""));

        // Do not block help-seeking/recovery queries just because they mention a topic.
        boolean supportIntent = false;
        for (String phrase : SAFE_SUPPORT_PHRASES) {
            String candidate = normalize(phrase);
            if (!candidate.isEmpty() &&
                    (padded.contains(" " + candidate + " ") ||
                     compact.contains(candidate.replace(" ", ""))) {
                supportIntent = true;
                break;
            }
        }

        // Branded adult sites and obvious names are always blocked, even in support queries.
        for (String marker : BLOCKED_COMPACT_MARKERS) {
            if (compact.contains(foldLeetspeak(marker))) return true;
        }

        // Explicit multiword searches are blocked. A clear help/recovery intent may mention
        // generic terms like "porn" or "gambling", but never overrides explicit-media phrases.
        for (String phrase : BLOCKED_PHRASES) {
            String candidate = normalize(phrase);
            if (candidate.isEmpty() || !padded.contains(" " + candidate + " ")) continue;
            boolean supportSensitive = false;
            for (String sensitive : SUPPORT_SENSITIVE_TERMS) {
                if (candidate.equals(normalize(sensitive))) {
                    supportSensitive = true;
                    break;
                }
            }
            if (supportIntent && supportSensitive) continue;
            return true;
        }

        if (supportIntent) return false;

        for (String term : GENERIC_BLOCKED_TERMS) {
            String normalizedTerm = normalize(term);
            if (padded.contains(" " + normalizedTerm + " ") ||
                    compact.contains(foldLeetspeak(normalizedTerm))) return true;
        }
        return false;
    }

    /** True when the URL hostname is in the local adult/gambling destination blocklist. */
    static boolean isBlockedDomain(String url) {
        URI uri = parseUri(url);
        if (uri == null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String domain : BLOCKED_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    /** True for known restricted domains or blocked terms submitted to supported search providers. */
    static boolean isBlockedUrl(String url) {
        return isBlockedDomain(url) || isBlockedSearchUrl(url);
    }

    private static String foldLeetspeak(String value) {
        return value.replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('@', 'a').replace('$', 's');
    }

    /** Decoded query for known search-provider URLs, or null for other pages. */
    static String extractSearchQuery(String url) {
        URI uri = parseUri(url);
        if (uri == null || uri.getHost() == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String key = queryKeyForHost(host);
        if (key == null || uri.getRawQuery() == null) return null;
        for (String parameter : uri.getRawQuery().split("&")) {
            int split = parameter.indexOf('=');
            String rawName = split < 0 ? parameter : parameter.substring(0, split);
            if (!decode(rawName).equalsIgnoreCase(key)) continue;
            return decode(split < 0 ? "" : parameter.substring(split + 1));
        }
        return null;
    }

    static boolean isBlockedSearchUrl(String url) {
        String query = extractSearchQuery(url);
        return query != null && containsBlockedSearchTerm(query);
    }

    static boolean isBraveSearchHostUrl(String url) {
        URI uri = parseUri(url);
        return uri != null && "search.brave.com".equalsIgnoreCase(uri.getHost());
    }

    /**
     * Apply strict search parameters only to recognized result-query URLs.
     * Startpage is intentionally not rewritten because a verified strict-mode
     * URL parameter is not available; the local phrase blocker still applies.
     */
    static String withStrictSafeSearch(String url) {
        URI uri = parseUri(url);
        if (uri == null || uri.getHost() == null) return url;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (queryKeyForHost(host) == null || extractSearchQuery(url) == null) return url;

        String key;
        String value;
        if (isGoogleHost(host)) {
            key = "safe"; value = "active";
        } else if (isBingHost(host)) {
            key = "adlt"; value = "strict";
        } else if (isDuckDuckGoHost(host)) {
            key = "kp"; value = "1";
        } else if ("search.brave.com".equals(host)) {
            key = "safesearch"; value = "strict";
        } else {
            return url;
        }

        String withoutFragment = url;
        String fragment = "";
        int fragmentAt = withoutFragment.indexOf('#');
        if (fragmentAt >= 0) {
            fragment = withoutFragment.substring(fragmentAt);
            withoutFragment = withoutFragment.substring(0, fragmentAt);
        }
        int queryAt = withoutFragment.indexOf('?');
        String prefix = queryAt >= 0 ? withoutFragment.substring(0, queryAt) : withoutFragment;
        String rawQuery = queryAt >= 0 ? withoutFragment.substring(queryAt + 1) : "";
        List<String> kept = new ArrayList<>();
        if (!rawQuery.isEmpty()) {
            for (String parameter : rawQuery.split("&")) {
                int split = parameter.indexOf('=');
                String rawName = split < 0 ? parameter : parameter.substring(0, split);
                if (!decode(rawName).equalsIgnoreCase(key)) kept.add(parameter);
            }
        }
        kept.add(key + "=" + value);
        return prefix + "?" + String.join("&", kept) + fragment;
    }

    static String createPinSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().withoutPadding().encodeToString(salt);
    }

    static String hashPin(String pin, String salt) throws GeneralSecurityException {
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), saltBytes, PIN_ITERATIONS, PIN_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return Base64.getEncoder().withoutPadding().encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    static boolean verifyPin(String candidate, String salt, String expectedHash) {
        if (candidate == null || salt == null || expectedHash == null
                || candidate.isEmpty() || salt.isEmpty() || expectedHash.isEmpty()) return false;
        try {
            byte[] actual = Base64.getDecoder().decode(hashPin(candidate, salt));
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (GeneralSecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String queryKeyForHost(String host) {
        if (isGoogleHost(host) || isBingHost(host) || isDuckDuckGoHost(host)
                || "search.brave.com".equals(host)) return "q";
        if (isStartpageHost(host)) return "query";
        return null;
    }

    private static boolean isGoogleHost(String host) {
        return host.equals("google.com") || host.endsWith(".google.com")
                || host.equals("google.co.in") || host.endsWith(".google.co.in")
                || host.equals("google.co.uk") || host.endsWith(".google.co.uk");
    }

    private static boolean isBingHost(String host) {
        return host.equals("bing.com") || host.endsWith(".bing.com");
    }

    private static boolean isDuckDuckGoHost(String host) {
        return host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com");
    }

    private static boolean isStartpageHost(String host) {
        return host.equals("startpage.com") || host.endsWith(".startpage.com");
    }

    private static URI parseUri(String url) {
        if (url == null || url.isEmpty()) return null;
        try { return new URI(url); } catch (Exception ignored) { return null; }
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception ignored) { return value; }
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
