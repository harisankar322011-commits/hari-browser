package com.haricalcx.haribrowser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local, best-effort search and destination filter for Teen Protection.
 *
 * This is intentionally conservative around educational health queries: it does not
 * block generic "sex education", "sexual health", or reproductive-health searches.
 * It is a keyword/domain filter, not a full safe-search service or page-content scanner.
 */
public final class TeenProtection {
    private TeenProtection() {}

    private static final int PIN_ITERATIONS = 120_000;
    private static final int PIN_BITS = 256;
    private static final int PIN_SALT_BYTES = 16;

    private static final Set<String> BLOCKED_DOMAINS = immutableSet(
            "pornhub.com", "xvideos.com", "xhamster.com", "xnxx.com", "redtube.com",
            "youporn.com", "tube8.com", "spankbang.com", "eporner.com", "hclips.com",
            "erome.com", "chaturbate.com", "stripchat.com", "cam4.com", "livejasmin.com",
            "rule34.xxx", "nhentai.net", "brazzers.com", "bangbros.com", "xhamsterlive.com",
            "onlyfans.com", "bet365.com", "stake.com", "betway.com", "1xbet.com",
            "betfair.com", "pokerstars.com", "888casino.com", "draftkings.com",
            "fanduel.com", "roobet.com", "bc.game", "parimatch.com"
    );

    private static final Set<String> SEARCH_HOSTS = immutableSet(
            "google.com", "google.co.in", "google.co.uk", "bing.com", "duckduckgo.com",
            "search.brave.com", "startpage.com", "yahoo.com", "ecosia.org", "yandex.com",
            "qwant.com", "swisscows.com", "youtube.com", "reddit.com", "tiktok.com"
    );

    private static final Set<String> SEARCH_PARAMETERS = immutableSet(
            "q", "query", "text", "search_query", "p", "keywords", "keyword", "term"
    );

    // High-specificity brand/site markers also catch punctuation and common leetspeak.
    private static final String[] BLOCKED_COMPACT_MARKERS = {
            "pornhub", "xvideos", "xhamster", "xnxx", "redtube", "youporn",
            "hentai", "rule34", "onlyfans", "chaturbate", "spankbang", "brazzers",
            "bangbros", "eporner", "hclips", "erome", "livejasmin", "stripchat",
            "sexcam", "sextape", "xxxvideo", "xxxvideos", "adultvideos"
    };

    private static final Set<String> BLOCKED_TOKENS = immutableSet(
            "porn", "pornography", "hentai", "nsfw", "nudes"
    );

    private static final String[] BLOCKED_PHRASES = {
            "free porn", "porn video", "porn videos", "porn site", "porn sites",
            "watch porn", "porn images", "porn clips", "adult video", "adult videos",
            "adult content sites", "nude photos", "nude pictures", "nude girls",
            "naked girls", "naked women", "naked pictures", "naked pics",
            "sex video", "sex videos", "sex cam", "sex cams", "webcam sex",
            "sex tape", "sex tapes", "erotic videos", "xxx videos", "xxx photos",
            "explicit photos", "explicit videos",
            "online casino", "online casinos", "casino games for money",
            "casino sites", "real money casino", "real money gambling",
            "online gambling", "gambling sites", "sports betting", "betting sites",
            "betting apps", "best betting apps", "poker for money", "real money poker",
            "slot machines online", "online slots", "slots for money", "bet now",
            "sportsbook", "sportsbooks", "bookmaker sites"
    };

    private static final String[] SAFE_SUPPORT_PHRASES = {
            "porn addiction", "stop watching porn", "help with porn",
            "porn recovery", "recover from pornography"
    };

    private static Set<String> immutableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /** Returns true for common explicit adult-content or real-money gambling searches. */
    public static boolean isBlockedSearch(String input) {
        if (input == null || input.trim().isEmpty()) return false;

        String normalized = normalize(input);
        String compact = normalized.replace(" ", "");

        // Explicit destination/brand names are always blocked, including in support queries.
        for (String marker : BLOCKED_COMPACT_MARKERS) {
            if (compact.contains(marker)) return true;
        }

        for (String phrase : BLOCKED_PHRASES) {
            if (containsPhrase(normalized, phrase)) return true;
        }

        // Preserve access to help and recovery resources while filtering explicit material.
        for (String phrase : SAFE_SUPPORT_PHRASES) {
            if (containsPhrase(normalized, phrase)) return false;
        }

        // Compact matching catches punctuation/spacing evasions such as "p.o.r.n".
        for (String marker : new String[]{"porn", "pornography", "hentai", "nsfw", "nudes"}) {
            if (compact.contains(marker)) return true;
        }
        for (String token : BLOCKED_TOKENS) {
            if (containsToken(normalized, token)) return true;
        }
        return false;
    }

    /**
     * Blocks known adult/gambling destination domains and searches with blocked terms
     * on common search engines. Does not scan the text/content of arbitrary web pages.
     */
    public static boolean isBlockedUrl(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);

            for (String domain : BLOCKED_DOMAINS) {
                if (host.equals(domain) || host.endsWith("." + domain)) return true;
            }

            boolean isSearchHost = false;
            for (String domain : SEARCH_HOSTS) {
                if (host.equals(domain) || host.endsWith("." + domain)) {
                    isSearchHost = true;
                    break;
                }
            }
            if (!isSearchHost || uri.getRawQuery() == null) return false;

            String[] parameters = uri.getRawQuery().split("&");
            for (String parameter : parameters) {
                int separator = parameter.indexOf('=');
                String rawName = separator >= 0 ? parameter.substring(0, separator) : parameter;
                String name = decodeRepeatedly(rawName).toLowerCase(Locale.ROOT);
                if (!SEARCH_PARAMETERS.contains(name) || separator < 0) continue;
                String query = decodeRepeatedly(parameter.substring(separator + 1));
                if (isBlockedSearch(query)) return true;
            }
        } catch (Exception ignored) {
            // Let the browser's ordinary URL validation handle malformed URLs.
        }
        return false;
    }

    private static String normalize(String input) {
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace("\u200b", "")
                .replace("\u200c", "")
                .replace("\u200d", "")
                .replace("\ufeff", "")
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('@', 'a')
                .replace('$', 's');
        return value.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean containsToken(String normalized, String token) {
        return (" " + normalized + " ").contains(" " + token + " ");
    }

    private static boolean containsPhrase(String normalized, String phrase) {
        return (" " + normalized + " ").contains(" " + phrase + " ");
    }

    private static String decodeRepeatedly(String value) {
        String decoded = value;
        for (int i = 0; i < 3; i++) {
            try {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8.name());
                if (next.equals(decoded)) break;
                decoded = next;
            } catch (Exception ignored) {
                break;
            }
        }
        return decoded;
    }

    /** Creates a fresh 128-bit salt for a parent PIN. */
    public static String newPinSalt() {
        byte[] salt = new byte[PIN_SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().withoutPadding().encodeToString(salt);
    }

    /** Derives a salted PIN hash; callers should store the result in encrypted storage. */
    public static String hashPin(String pin, String encodedSalt) throws GeneralSecurityException {
        if (pin == null || encodedSalt == null) throw new GeneralSecurityException("Missing PIN data");
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(encodedSalt);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Invalid PIN salt", error);
        }
        PBEKeySpec specification = new PBEKeySpec(pin.toCharArray(), salt, PIN_ITERATIONS, PIN_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
            return Base64.getEncoder().withoutPadding().encodeToString(hash);
        } finally {
            specification.clearPassword();
        }
    }

    /** Constant-time comparison of a candidate PIN against its stored salted hash. */
    public static boolean verifyPin(String pin, String encodedSalt, String expectedHash) {
        if (pin == null || encodedSalt == null || expectedHash == null ||
                encodedSalt.isEmpty() || expectedHash.isEmpty()) return false;
        try {
            byte[] actual = Base64.getDecoder().decode(hashPin(pin, encodedSalt));
            byte[] expected = Base64.getDecoder().decode(expectedHash);
            return MessageDigest.isEqual(actual, expected);
        } catch (Exception ignored) {
            return false;
        }
    }
}
