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
    static final int MAX_ALLOWED_SAVED_PAGES = 12;
    static final int MAX_ALLOWED_BODY_CHARS = 32000;
    static final int MIN_PREFERRED_BODY_CHARS = 8000;
    private static final int MAX_TITLE_CHARS = 180;
    private static final int MAX_URL_CHARS = 4096;

    private OfflineReading() {}

    static JSONArray parse(String serialized) {
        return parse(serialized, MAX_SAVED_PAGES, MAX_BODY_CHARS);
    }

    static JSONArray parse(String serialized, int requestedPages, int requestedBodyChars) {
        int maxPages = clampPages(requestedPages);
        int maxBodyChars = clampBodyChars(requestedBodyChars);
        JSONArray cleaned = new JSONArray();
        final JSONArray source;
        try {
            source = new JSONArray(serialized == null ? "[]" : serialized);
        } catch (JSONException invalid) {
            return cleaned;
        }

        for (int i = 0; i < source.length() && cleaned.length() < maxPages; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url", "");
            String body = limitText(item.optString("body", ""), maxBodyChars);
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
        return addOrReplace(serialized, title, url, body, savedAt,
                MAX_SAVED_PAGES, MAX_BODY_CHARS);
    }

    static JSONArray addOrReplace(String serialized, String title, String url,
                                  String body, long savedAt, int requestedPages,
                                  int requestedBodyChars) {
        int maxPages = clampPages(requestedPages);
        int maxBodyChars = clampBodyChars(requestedBodyChars);
        String cleanUrl = url == null ? "" : url.trim();
        String cleanBody = limitText(body, maxBodyChars);
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

            JSONArray previous = parse(serialized, maxPages, maxBodyChars);
            for (int i = 0; i < previous.length() && result.length() < maxPages; i++) {
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
        return removeByUrl(serialized, url, MAX_SAVED_PAGES, MAX_BODY_CHARS);
    }

    static JSONArray removeByUrl(String serialized, String url, int requestedPages,
                                 int requestedBodyChars) {
        JSONArray result = new JSONArray();
        JSONArray saved = parse(serialized, requestedPages, requestedBodyChars);
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null || item.optString("url", "").equals(url)) continue;
            result.put(item);
        }
        return result;
    }

    static String limitText(String text) {
        return limitText(text, MAX_BODY_CHARS);
    }

    static String limitText(String text, int requestedBodyChars) {
        if (text == null) return "";
        String normalized = text.trim();
        int maxBodyChars = clampBodyChars(requestedBodyChars);
        if (normalized.length() > maxBodyChars) {
            normalized = normalized.substring(0, maxBodyChars);
        }
        // Avoid ending on an unpaired high surrogate if the limit splits a pair.
        if (!normalized.isEmpty() && Character.isHighSurrogate(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    static String decodeJavascriptString(String encodedValue) {
        return decodeJavascriptString(encodedValue, MAX_BODY_CHARS);
    }

    static String decodeJavascriptString(String encodedValue, int requestedBodyChars) {
        if (encodedValue == null || encodedValue.trim().isEmpty() || "null".equals(encodedValue.trim())) {
            return "";
        }
        try {
            JSONArray wrapper = new JSONArray("[" + encodedValue + "]");
            Object value = wrapper.opt(0);
            return value instanceof String ? limitText((String) value, requestedBodyChars) : "";
        } catch (JSONException malformed) {
            return "";
        }
    }

    private static int clampPages(int requested) {
        return Math.max(1, Math.min(MAX_ALLOWED_SAVED_PAGES, requested));
    }

    private static int clampBodyChars(int requested) {
        return Math.max(1, Math.min(MAX_ALLOWED_BODY_CHARS, requested));
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
