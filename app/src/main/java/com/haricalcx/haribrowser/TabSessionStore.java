package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded, HTTPS-only serialization for the normal-mode tab session.
 * The caller must persist the returned JSON through SecureStore; this class
 * deliberately contains no persistence or private-profile access.
 */
final class TabSessionStore {
    static final int MAX_TABS = 20;
    private static final int MAX_URL_LENGTH = 8192;

    static final class Session {
        final List<String> urls;
        final int selectedIndex;

        Session(List<String> urls, int selectedIndex) {
            this.urls = Collections.unmodifiableList(new ArrayList<>(urls));
            this.selectedIndex = urls.isEmpty() ? 0 :
                    Math.max(0, Math.min(selectedIndex, urls.size() - 1));
        }
    }

    private TabSessionStore() {}

    static String encode(List<String> urls, int selectedIndex) {
        JSONArray array = new JSONArray();
        int count = 0;
        if (urls != null) {
            for (String url : urls) {
                if (count >= MAX_TABS) break;
                if (url == null || url.isEmpty()) {
                    array.put("");
                    count++;
                } else if (isSafeHttpsUrl(url)) {
                    array.put(url);
                    count++;
                }
            }
        }
        int safeIndex = array.length() == 0 ? 0 :
                Math.max(0, Math.min(selectedIndex, array.length() - 1));
        JSONArray payload = new JSONArray();
        payload.put(array);
        payload.put(safeIndex);
        return payload.toString();
    }

    static Session decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_TABS * MAX_URL_LENGTH + 256) {
            return new Session(Collections.emptyList(), 0);
        }
        try {
            JSONArray payload = new JSONArray(encoded);
            if (payload.length() != 2 || !(payload.get(0) instanceof JSONArray)) {
                return new Session(Collections.emptyList(), 0);
            }
            JSONArray array = payload.getJSONArray(0);
            ArrayList<String> urls = new ArrayList<>();
            for (int i = 0; i < array.length() && urls.size() < MAX_TABS; i++) {
                Object item = array.opt(i);
                if (!(item instanceof String)) continue;
                String url = (String) item;
                if (url.isEmpty() || isSafeHttpsUrl(url)) urls.add(url);
            }
            return new Session(urls, payload.optInt(1, 0));
        } catch (JSONException ignored) {
            return new Session(Collections.emptyList(), 0);
        }
    }

    static boolean isSafeHttpsUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_URL_LENGTH) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) &&
                    uri.getHost() != null && !uri.getHost().isEmpty() &&
                    uri.getRawUserInfo() == null && uri.getPort() <= 65535;
        } catch (Exception ignored) {
            return false;
        }
    }
}
