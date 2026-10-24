package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class BookmarkFolders {
    static final String DEFAULT_FOLDER = "Unfiled";
    static final String ALL_BOOKMARKS = "All bookmarks";
    static final int MAX_FOLDER_NAME_LENGTH = 40;
    static final int MAX_FOLDERS = 50;

    private BookmarkFolders() {}

    static String normalizeFolderName(String value) {
        if (value == null) return "";
        StringBuilder clean = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            clean.append(Character.isISOControl(ch) ? ' ' : ch);
        }
        String result = clean.toString().trim().replaceAll("\\s+", " ");
        if (result.length() > MAX_FOLDER_NAME_LENGTH) {
            result = result.substring(0, MAX_FOLDER_NAME_LENGTH).trim();
        }
        if (result.equalsIgnoreCase(DEFAULT_FOLDER)) return DEFAULT_FOLDER;
        if (result.equalsIgnoreCase(ALL_BOOKMARKS)) return ALL_BOOKMARKS;
        return result;
    }

    static String folderFor(JSONObject item) {
        if (item == null) return DEFAULT_FOLDER;
        String folder = normalizeFolderName(item.optString("folder", ""));
        return folder.isEmpty() || ALL_BOOKMARKS.equals(folder) ? DEFAULT_FOLDER : folder;
    }

    static List<String> folderNames(String serialized) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add(DEFAULT_FOLDER);
        JSONArray source = parse(serialized);
        for (int i = 0; i < source.length() && names.size() < MAX_FOLDERS; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null || !PrivacyGuard.isHttpsUrl(item.optString("url", ""))) continue;
            String folder = folderFor(item);
            if (!folder.isEmpty() && !ALL_BOOKMARKS.equals(folder)) names.add(folder);
        }
        return new ArrayList<>(names);
    }

    static String filterByFolder(String serialized, String requestedFolder) {
        JSONArray source = parse(serialized);
        String folder = normalizeFolderName(requestedFolder);
        boolean all = folder.isEmpty() || ALL_BOOKMARKS.equals(folder);
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null || !PrivacyGuard.isHttpsUrl(item.optString("url", ""))) continue;
            if (all || folder.equals(folderFor(item))) result.put(item);
        }
        return result.toString();
    }

    static String move(String serialized, String url, String requestedFolder) {
        JSONArray source = parse(serialized);
        String folder = normalizeFolderName(requestedFolder);
        if (!PrivacyGuard.isHttpsUrl(url) || folder.isEmpty() || ALL_BOOKMARKS.equals(folder)) {
            return source.toString();
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null || !url.equals(item.optString("url", ""))
                    || !PrivacyGuard.isHttpsUrl(item.optString("url", ""))) continue;
            if (DEFAULT_FOLDER.equals(folder)) item.remove("folder");
            else {
                try {
                    item.put("folder", folder);
                } catch (JSONException invalid) {
                    return source.toString();
                }
            }
            return source.toString();
        }
        return source.toString();
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

    private static JSONArray parse(String serialized) {
        try {
            return new JSONArray(serialized == null ? "[]" : serialized);
        } catch (JSONException invalid) {
            return new JSONArray();
        }
    }
}
