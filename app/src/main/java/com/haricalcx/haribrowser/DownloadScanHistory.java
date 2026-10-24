package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Small, bounded and sanitized history of local download-scan reports. */
final class DownloadScanHistory {
    static final int MAX_ENTRIES = 20;
    static final int MAX_FILENAME_CHARS = 180;
    static final int MAX_DETAILS_CHARS = 1800;

    private DownloadScanHistory() {}

    static JSONArray parse(String serialized) {
        JSONArray cleaned = new JSONArray();
        final JSONArray source;
        try {
            source = new JSONArray(serialized == null ? "[]" : serialized);
        } catch (JSONException invalid) {
            return cleaned;
        }

        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < source.length() && cleaned.length() < MAX_ENTRIES; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            long id = item.optLong("id", -1L);
            if (id < 0 || !seenIds.add(id)) continue;
            try {
                JSONObject safe = new JSONObject();
                safe.put("id", id);
                safe.put("filename", cleanFilename(item.optString("filename", "Downloaded file")));
                safe.put("status", cleanStatus(item.optString("status", "SCAN_INCOMPLETE")));
                safe.put("details", limitDetails(item.optString("details", "")));
                String hash = item.optString("sha256", "");
                safe.put("sha256", hash.matches("(?i)[0-9a-f]{64}") ? hash.toLowerCase(java.util.Locale.ROOT) : "");
                safe.put("scannedAt", Math.max(0L, item.optLong("scannedAt", 0L)));
                cleaned.put(safe);
            } catch (JSONException ignored) {
                // Invalid individual records are dropped instead of breaking the list.
            }
        }
        return cleaned;
    }

    static JSONArray append(String serialized, long id, String filename, String status,
                            String details, String sha256, long scannedAt) {
        if (id < 0) throw new IllegalArgumentException("A valid download ID is required.");
        JSONArray result = new JSONArray();
        try {
            JSONObject newest = new JSONObject();
            newest.put("id", id);
            newest.put("filename", cleanFilename(filename));
            newest.put("status", cleanStatus(status));
            newest.put("details", limitDetails(details));
            String hash = sha256 == null ? "" : sha256.trim();
            newest.put("sha256", hash.matches("(?i)[0-9a-f]{64}") ? hash.toLowerCase(java.util.Locale.ROOT) : "");
            newest.put("scannedAt", Math.max(0L, scannedAt));
            result.put(newest);

            JSONArray existing = parse(serialized);
            for (int i = 0; i < existing.length() && result.length() < MAX_ENTRIES; i++) {
                JSONObject item = existing.optJSONObject(i);
                if (item == null || item.optLong("id", -1L) == id) continue;
                result.put(item);
            }
        } catch (JSONException impossibleForValidValues) {
            throw new IllegalStateException("Could not record the download scan result.", impossibleForValidValues);
        }
        return result;
    }

    static String format(JSONObject item) {
        if (item == null) return "No scan details are available.";
        String filename = cleanFilename(item.optString("filename", "Downloaded file"));
        String status = cleanStatus(item.optString("status", "SCAN_INCOMPLETE"));
        StringBuilder message = new StringBuilder();
        message.append("File: ").append(filename).append("\n");
        message.append("Result: ").append(status).append("\n");
        message.append("Scanned: ").append(Math.max(0L, item.optLong("scannedAt", 0L))).append("\n\n");
        message.append(limitDetails(item.optString("details", ""))).append("\n\n");
        String hash = item.optString("sha256", "");
        message.append("SHA-256: ").append(hash.matches("(?i)[0-9a-f]{64}") ?
                hash.toLowerCase(java.util.Locale.ROOT) : "unavailable");
        return message.toString();
    }

    private static String cleanFilename(String value) {
        String cleaned = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (cleaned.isEmpty()) cleaned = "Downloaded file";
        return cleaned.length() > MAX_FILENAME_CHARS ? cleaned.substring(0, MAX_FILENAME_CHARS) : cleaned;
    }

    private static String cleanStatus(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "");
        if (cleaned.isEmpty()) cleaned = "SCAN_INCOMPLETE";
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    private static String limitDetails(String value) {
        String cleaned = value == null ? "" : value.replace('\r', '\n').trim();
        if (cleaned.length() > MAX_DETAILS_CHARS) cleaned = cleaned.substring(0, MAX_DETAILS_CHARS);
        return cleaned;
    }
}
