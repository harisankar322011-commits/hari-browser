package com.haricalcx.haribrowser;

import java.net.URI;
import java.net.IDN;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Performs a local, URL-structure preflight before the browser navigates.
 *
 * This is deliberately network-free. It does not claim to verify the site's
 * certificate, response headers, page contents, reputation, or malware status.
 * Android WebView TLS validation and Safe Browsing remain separate checks.
 */
public final class SiteSafetyChecker {
    private SiteSafetyChecker() {}

    public static final class Report {
        private final String host;
        private final List<String> passedChecks;
        private final List<String> warnings;

        private Report(String host, List<String> passedChecks, List<String> warnings) {
            this.host = host;
            this.passedChecks = Collections.unmodifiableList(new ArrayList<>(passedChecks));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public String getHost() {
            return host;
        }

        public List<String> getPassedChecks() {
            return passedChecks;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /** Inspect only the supplied URL. Does not perform a network request. */
    public static Report inspect(String value) {
        List<String> passed = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String hostLabel = "unknown host";

        if (value == null || value.trim().isEmpty()) {
            warnings.add("No website address was provided.");
            return new Report(hostLabel, passed, warnings);
        }

        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (Exception invalidUri) {
            warnings.add("The address has an unusual or invalid URL format.");
            return new Report(hostLabel, passed, warnings);
        }

        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            passed.add("HTTPS address requested");
        } else {
            warnings.add("The address is not HTTPS. Hari Browser only opens HTTPS websites.");
        }

        String host = uri.getHost();
        // java.net.URI returns null from getHost() for Unicode domain labels. Parse the
        // authority conservatively so those addresses receive a specific warning instead
        // of being reduced to a generic "unknown host" message.
        if (host == null || host.trim().isEmpty()) {
            host = hostFromAuthority(uri);
        }
        if (host == null || host.trim().isEmpty()) {
            warnings.add("The browser could not reliably identify the destination hostname.");
        } else {
            String normalizedHost;
            try {
                normalizedHost = host.indexOf(':') >= 0
                        ? host.toLowerCase(Locale.ROOT)
                        : IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException invalidHostname) {
                hostLabel = host;
                normalizedHost = null;
                warnings.add("The destination hostname contains invalid or unsupported internationalized characters.");
            }

            if (normalizedHost != null) {
                hostLabel = normalizedHost;

                if (containsNonAscii(host)) {
                    warnings.add("The hostname is an internationalized domain name. Confirm the spelling carefully; visually similar characters may disguise the destination.");
                }
                String unicodeHost = IDN.toUnicode(normalizedHost);
                if (hasMixedLatinCyrillicOrGreekLabel(unicodeHost)) {
                    warnings.add("A hostname label mixes Latin with Cyrillic or Greek characters, a pattern sometimes used for visual spoofing.");
                }

                String listedService = OfficialDomainRegistry.serviceForHost(normalizedHost);
                if (listedService != null) {
                    passed.add("Hostname matches the curated official-domain list: " + listedService
                            + " (a list match, not a guarantee that this page is safe)");
                }

                if (uri.getUserInfo() != null) {
                    warnings.add("The URL contains text before the hostname that can disguise the real destination.");
                } else {
                    passed.add("No embedded username or password in the URL");
                }

                if (normalizedHost.startsWith("xn--") || normalizedHost.contains(".xn--")) {
                    warnings.add("The hostname contains punycode. Confirm the spelling carefully; internationalized names can be legitimate.");
                } else {
                    passed.add("Hostname is not visibly punycode-encoded");
                }

                if (isIpLiteral(normalizedHost)) {
                    warnings.add("The destination uses an IP address instead of a familiar domain name.");
                } else {
                    passed.add("Destination is a named hostname, not an IP literal");
                }

                if (normalizedHost.length() > 100) {
                    warnings.add("The hostname is unusually long.");
                }

                if (countLabels(normalizedHost) > 5) {
                    warnings.add("The hostname has many subdomains. Check the registered domain carefully.");
                }

                int port = uri.getPort();
                if (port == -1 || port == 443) {
                    passed.add("No unusual HTTPS port");
                } else {
                    warnings.add("The URL uses a non-standard HTTPS port (" + port + ").");
                }
            }
        }

        String query = uri.getRawQuery();
        if (query != null && query.length() > 500) {
            warnings.add("The URL has a very long query string that may contain tracking or login tokens. Avoid sharing it.");
        }

        // Conservative India-focused scam checks inspect only the URL, not page text or reputation.
        warnings.addAll(IndianScamChecker.inspect(value));

        if (warnings.isEmpty()) {
            passed.add("No obvious URL-structure warning found (this is not a trust or malware verdict)");
        }
        return new Report(hostLabel, passed, warnings);
    }

    private static String hostFromAuthority(URI uri) {
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty()) return null;

        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        if (authority.isEmpty()) return null;

        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            return closingBracket > 0 ? authority.substring(1, closingBracket) : null;
        }

        int firstColon = authority.indexOf(':');
        int lastColon = authority.lastIndexOf(':');
        if (firstColon >= 0 && firstColon == lastColon) {
            String port = authority.substring(lastColon + 1);
            if (port.isEmpty() || !port.chars().allMatch(Character::isDigit)) return null;
            authority = authority.substring(0, lastColon);
        } else if (firstColon >= 0) {
            // An unbracketed multi-colon authority is ambiguous; do not guess its host.
            return null;
        }
        return authority.isEmpty() ? null : authority;
    }

    private static boolean containsNonAscii(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) return true;
        }
        return false;
    }

    /** Only flags mixed Latin/Cyrillic/Greek letters within the same hostname label. */
    private static boolean hasMixedLatinCyrillicOrGreekLabel(String hostname) {
        if (hostname == null || hostname.isEmpty()) return false;
        String unicodeHost = IDN.toUnicode(hostname);
        for (String label : unicodeHost.split("\\.", -1)) {
            boolean latin = false;
            boolean cyrillic = false;
            boolean greek = false;
            for (int offset = 0; offset < label.length();) {
                int codePoint = label.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (!Character.isLetter(codePoint)) continue;
                Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
                if (script == Character.UnicodeScript.LATIN) latin = true;
                else if (script == Character.UnicodeScript.CYRILLIC) cyrillic = true;
                else if (script == Character.UnicodeScript.GREEK) greek = true;
            }
            if ((latin && (cyrillic || greek)) || (cyrillic && greek)) return true;
        }
        return false;
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true; // IPv6 literal
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            try {
                if (Integer.parseInt(part) > 255) return false;
            } catch (NumberFormatException error) {
                return false;
            }
        }
        return true;
    }

    private static int countLabels(String host) {
        int labels = 1;
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) == '.') labels++;
        }
        return labels;
    }
}
