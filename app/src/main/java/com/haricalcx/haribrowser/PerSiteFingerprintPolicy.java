package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Bounded per-host exceptions for the best-effort anti-fingerprinting document-start script. */
final class PerSiteFingerprintPolicy {
    static final int MAX_EXEMPT_HOSTS = 128;
    private static final int MAX_STORED_CHARS = 40_000;

    private PerSiteFingerprintPolicy() {}

    static Set<String> parse(String serialized) {
        if (serialized == null || serialized.length() > MAX_STORED_CHARS) {
            return Collections.emptySet();
        }
        final JSONArray source;
        try {
            source = new JSONArray(serialized);
        } catch (JSONException invalid) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (int i = 0; i < source.length() && hosts.size() < MAX_EXEMPT_HOSTS; i++) {
            String normalized = normalizeHost(source.optString(i, ""));
            if (normalized != null) hosts.add(normalized);
        }
        return Collections.unmodifiableSet(hosts);
    }

    static boolean isExempt(String serialized, String host) {
        String normalized = normalizeHost(host);
        return normalized != null && parse(serialized).contains(normalized);
    }

    /** Toggle an exact hostname. When full, adding an exception evicts the oldest one. */
    static String toggle(String serialized, String host) {
        String normalized = normalizeHost(host);
        if (normalized == null) {
            throw new IllegalArgumentException("A valid site hostname is required.");
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>(parse(serialized));
        if (hosts.remove(normalized)) {
            // Removing an exception re-enables protection on that exact hostname.
        } else {
            if (hosts.size() >= MAX_EXEMPT_HOSTS) {
                java.util.Iterator<String> oldest = hosts.iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            hosts.add(normalized);
        }

        JSONArray output = new JSONArray();
        for (String entry : hosts) output.put(entry);
        return output.toString();
    }

    static String normalizeHost(String host) {
        if (host == null) return null;
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.length() > 253 || normalized.contains("..")) {
            return null;
        }
        if ("localhost".equals(normalized)) return normalized;

        if (normalized.indexOf(':') >= 0) {
            // Uri.getHost() can return an IPv6 literal without brackets.
            return normalized.matches("[0-9a-f:.]+") ? normalized : null;
        }
        if (normalized.matches("[0-9]+(?:\\.[0-9]+){3}")) {
            String[] octets = normalized.split("\\.");
            for (String octet : octets) {
                try {
                    int value = Integer.parseInt(octet);
                    if (value < 0 || value > 255) return null;
                } catch (NumberFormatException invalid) {
                    return null;
                }
            }
            return normalized;
        }

        String[] labels = normalized.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 ||
                    !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) {
                return null;
            }
        }
        return normalized;
    }
}
