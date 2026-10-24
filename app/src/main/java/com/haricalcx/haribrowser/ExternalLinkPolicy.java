package com.haricalcx.haribrowser;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Strict allowlist for external link types that can safely leave the browser. */
final class ExternalLinkPolicy {
    enum Kind { NONE, HTTP, HTTPS, MAILTO, TEL, BLOCKED }

    private static final int MAX_INPUT_CHARS = 2048;
    private static final Pattern EXPLICIT_SCHEME =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$", Pattern.DOTALL);
    private static final Pattern EMAIL_LOCAL = Pattern.compile("[A-Za-z0-9._+\\-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?");
    private static final String PHONE_FORMAT_CHARS = "0123456789+(). -";

    private ExternalLinkPolicy() {}

    static Kind classify(String input) {
        if (input == null || input.trim().isEmpty()) return Kind.NONE;
        if (input.length() > MAX_INPUT_CHARS || hasControl(input)) return Kind.BLOCKED;
        if (!EXPLICIT_SCHEME.matcher(input).matches()) return Kind.NONE;

        int colon = input.indexOf(':');
        String scheme = input.substring(0, colon).toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "https":
                return isWebUrl(input, "https") ? Kind.HTTPS : Kind.BLOCKED;
            case "http":
                return isWebUrl(input, "http") ? Kind.HTTP : Kind.BLOCKED;
            case "mailto":
                return safeMailTarget(input) == null ? Kind.BLOCKED : Kind.MAILTO;
            case "tel":
                return safePhoneTarget(input) == null ? Kind.BLOCKED : Kind.TEL;
            default:
                return Kind.BLOCKED;
        }
    }

    /**
     * Returns a canonical URI only for validated mailto/tel inputs. Query metadata
     * for mailto is deliberately discarded so subject/body/header tricks cannot
     * be passed through to an external app; the user confirms the recipient first.
     */
    static String safeExternalUri(String input) {
        Kind kind = classify(input);
        if (kind == Kind.MAILTO) return "mailto:" + safeMailTarget(input);
        if (kind == Kind.TEL) return "tel:" + safePhoneTarget(input);
        return null;
    }

    static String confirmationTarget(String input) {
        String canonical = safeExternalUri(input);
        if (canonical == null) return "";
        int colon = canonical.indexOf(':');
        return colon < 0 ? "" : canonical.substring(colon + 1);
    }

    private static boolean isWebUrl(String value, String expectedScheme) {
        try {
            URI uri = new URI(value);
            return expectedScheme.equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (Exception invalid) {
            return false;
        }
    }

    private static String safeMailTarget(String input) {
        if (input == null || input.length() > MAX_INPUT_CHARS || hasControl(input) ||
                hasEncodedLineBreak(input)) return null;
        String remainder = input.substring(input.indexOf(':') + 1);
        int query = remainder.indexOf('?');
        if (query >= 0) remainder = remainder.substring(0, query);
        int fragment = remainder.indexOf('#');
        if (fragment >= 0) remainder = remainder.substring(0, fragment);
        if (remainder.length() > 254 || remainder.isEmpty() || remainder.indexOf('%') >= 0 ||
                remainder.indexOf(',') >= 0 || remainder.indexOf(';') >= 0 ||
                remainder.indexOf('\\') >= 0 || remainder.indexOf('/') >= 0 ||
                remainder.indexOf(':') >= 0 || remainder.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }

        int at = remainder.indexOf('@');
        if (at <= 0 || at != remainder.lastIndexOf('@') || at == remainder.length() - 1) return null;
        String local = remainder.substring(0, at);
        String domain = remainder.substring(at + 1);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".") ||
                local.contains("..") || !EMAIL_LOCAL.matcher(local).matches()) return null;
        if (domain.length() > 253 || domain.startsWith(".") || domain.endsWith(".") ||
                domain.contains("..") || !domain.contains(".")) return null;
        String[] labels = domain.split("\\.", -1);
        for (String label : labels) {
            if (label.length() > 63 || !DOMAIN_LABEL.matcher(label).matches()) return null;
        }
        return local + "@" + domain.toLowerCase(Locale.ROOT);
    }

    private static String safePhoneTarget(String input) {
        if (input == null || input.length() > MAX_INPUT_CHARS || hasControl(input) ||
                hasEncodedLineBreak(input)) return null;
        String value = input.substring(input.indexOf(':') + 1);
        // Keep the allowlist narrow: extensions, fragments, and query parameters are not forwarded.
        if (value.indexOf(';') >= 0 || value.indexOf('?') >= 0 || value.indexOf('#') >= 0) return null;
        if (value.isEmpty()) return null;

        StringBuilder digits = new StringBuilder();
        boolean plusSeen = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (PHONE_FORMAT_CHARS.indexOf(c) < 0) return null;
            if (c == '+') {
                if (plusSeen || digits.length() != 0) return null;
                plusSeen = true;
            } else if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        if (digits.length() < 3 || digits.length() > 20) return null;
        return (plusSeen ? "+" : "") + digits;
    }

    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) return true;
        }
        return false;
    }

    private static boolean hasEncodedLineBreak(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("%0a") || lower.contains("%0d");
    }
}
