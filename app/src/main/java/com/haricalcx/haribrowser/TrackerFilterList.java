package com.haricalcx.haribrowser;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Parser for bounded hosts-format tracker lists; intentionally does not accept executable rules. */
final class TrackerFilterList {
    static final int MAX_TEXT_CHARS = 512 * 1024;
    static final int MAX_LINES = 20_000;
    static final int MAX_HOSTS = 10_000;

    private TrackerFilterList() {}

    static Set<String> parseHosts(String text) {
        if (text == null || text.isEmpty()) return Collections.emptySet();
        if (text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("The tracker list is larger than 512 KiB.");
        }

        String[] lines = text.split("\\r?\\n", MAX_LINES + 1);
        if (lines.length > MAX_LINES) {
            throw new IllegalArgumentException("The tracker list contains too many lines.");
        }

        Set<String> hosts = new LinkedHashSet<>();
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("//")) continue;
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment).trim();
            if (line.isEmpty()) continue;

            String[] tokens = line.split("\\s+");
            int firstHost = 0;
            if (tokens.length > 1 && isLoopbackAddress(tokens[0])) firstHost = 1;
            for (int i = firstHost; i < tokens.length; i++) {
                String candidate = tokens[i].toLowerCase(Locale.ROOT);
                // Ignore unsupported mapping directives and invalid tokens instead of interpreting them.
                if (!isDomain(candidate)) continue;
                hosts.add(candidate);
                if (hosts.size() > MAX_HOSTS) {
                    throw new IllegalArgumentException("The tracker list exceeds 10,000 unique domains.");
                }
            }
        }
        return Collections.unmodifiableSet(hosts);
    }

    static boolean isDomain(String value) {
        if (value == null || value.length() > 253 || value.length() < 3 ||
                !value.contains(".") || value.startsWith(".") || value.endsWith(".") ||
                value.contains("..") || "localhost".equals(value)) return false;
        String[] labels = value.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 ||
                    !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) return false;
        }
        return true;
    }

    private static boolean isLoopbackAddress(String value) {
        return "0.0.0.0".equals(value) || "127.0.0.1".equals(value) ||
                "::".equals(value) || "::1".equals(value);
    }
}
