package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

/**
 * Bounded, text-only offline reading snapshots. This class never evaluates saved
 * text as HTML and stores no scripts, images, cookies, or live page state.
 */
final class OfflineReading {
    static final int MAX_SAVED_PAGES = 8;
    static final int MAX_BODY_CHARS = 24000;
    private static final int MAX_TITLE_CHARS = 180;
    private static final int MAX_URL_CHARS = 4096;

    private OfflineReading() {}

    static JSONArray parse(String serialized) {
        JSONArray cleaned = new JSONArray();
        final JSONArray source;
        try {
            source = new JSONArray(serialized == null ? "[]" : serialized);
        } catch (JSONException invalid) {
            return cleaned;
        }

        for (int i = 0; i < source.length() && cleaned.length() < MAX_SAVED_PAGES; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url", "");
            String body = limitText(item.optString("body", ""));
            if (!isSafeHttpsUrl(url) || body.isEmpty()) continue;
            try {
                JSONObject safe = new JSONObject();
                safe.put("title", limitTitle(item.optString("title", "Saved page")));
                safe.put("url", url);
                safe.put("body", body);
                safe.put("savedAt", Math.max(0L, item.optLong("savedAt", 0L)));
                cleaned.put(safe);
            } catch (JSONException ignored) {
                // An invalid record is skipped rather than shown as saved content.
            }
        }
        return cleaned;
    }

    static JSONArray addOrReplace(String serialized, String title, String url,
                                  String body, long savedAt) {
        String cleanUrl = url == null ? "" : url.trim();
        String cleanBody = limitText(body);
        if (!isSafeHttpsUrl(cleanUrl)) {
            throw new IllegalArgumentException("Only valid HTTPS pages can be saved.");
        }
        if (cleanBody.isEmpty()) {
            throw new IllegalArgumentException("The page has no readable text to save.");
        }

        JSONArray result = new JSONArray();
        try {
            JSONObject newest = new JSONObject();
            newest.put("title", limitTitle(title));
            newest.put("url", cleanUrl.substring(0, Math.min(cleanUrl.length(), MAX_URL_CHARS)));
            newest.put("body", cleanBody);
            newest.put("savedAt", Math.max(0L, savedAt));
            result.put(newest);

            JSONArray previous = parse(serialized);
            for (int i = 0; i < previous.length() && result.length() < MAX_SAVED_PAGES; i++) {
                JSONObject item = previous.optJSONObject(i);
                if (item == null || cleanUrl.equals(item.optString("url", ""))) continue;
                result.put(item);
            }
        } catch (JSONException impossibleForValidValues) {
            throw new IllegalStateException("Could not prepare the offline snapshot.", impossibleForValidValues);
        }
        return result;
    }

    static JSONArray removeByUrl(String serialized, String url) {
        JSONArray result = new JSONArray();
        JSONArray saved = parse(serialized);
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null || item.optString("url", "").equals(url)) continue;
            result.put(item);
        }
        return result;
    }

    static String limitText(String text) {
        if (text == null) return "";
        String normalized = text.trim();
        if (normalized.length() > MAX_BODY_CHARS) {
            normalized = normalized.substring(0, MAX_BODY_CHARS);
        }
        // Avoid ending on an unpaired high surrogate if the limit splits a pair.
        if (!normalized.isEmpty() && Character.isHighSurrogate(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String decodeJavascriptString(String encodedValue) {
        if (encodedValue == null || encodedValue.trim().isEmpty() || "null".equals(encodedValue.trim())) {
            return "";
        }
        try {
            JSONArray wrapper = new JSONArray("[" + encodedValue + "]");
            Object value = wrapper.opt(0);
            return value instanceof String ? limitText((String) value) : "";
        } catch (JSONException malformed) {
            return "";
        }
    }

    private static String limitTitle(String title) {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty()) clean = "Saved page";
        return clean.length() > MAX_TITLE_CHARS ? clean.substring(0, MAX_TITLE_CHARS) : clean;
    }

    private static boolean isSafeHttpsUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_CHARS) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (Exception invalid) {
            return false;
        }
    }
}
