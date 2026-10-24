package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, backward-compatible reorder operation for the existing encrypted bookmark array. */
final class BookmarkOrder {
    private BookmarkOrder() {}

    /**
     * Moves the first matching valid HTTPS bookmark one visible position up (-1) or down (+1).
     * Other JSON fields and unrelated records are preserved. Invalid data fails closed to an empty array.
     */
    static String move(String serialized, String url, int direction) {
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("Direction must be -1 (up) or 1 (down).");
        }

        JSONArray source;
        try {
            source = new JSONArray(serialized == null ? "[]" : serialized);
        } catch (JSONException invalid) {
            return "[]";
        }

        if (!isSafeHttpsUrl(url)) return source.toString();
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) items.add(source.opt(i));

        int target = -1;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = asObject(items.get(i));
            if (item != null && isSafeHttpsUrl(item.optString("url", ""))
                    && url.equals(item.optString("url", ""))) {
                target = i;
                break;
            }
        }
        if (target < 0) return source.toString();

        int adjacent = target + direction;
        while (adjacent >= 0 && adjacent < items.size() && !isSafeBookmark(items.get(adjacent))) {
            adjacent += direction;
        }
        if (adjacent < 0 || adjacent >= items.size()) return source.toString();

        Collections.swap(items, target, adjacent);
        JSONArray result = new JSONArray();
        for (Object item : items) result.put(item);
        return result.toString();
    }

    private static boolean isSafeBookmark(Object value) {
        JSONObject item = asObject(value);
        return item != null && isSafeHttpsUrl(item.optString("url", ""));
    }

    private static JSONObject asObject(Object value) {
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    private static boolean isSafeHttpsUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && uri.getRawUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }
}
