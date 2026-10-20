package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Opt-in, manual URL reputation lookup via the URLhaus community API.
 * Called only after the user confirms that the URL may be shared with abuse.ch.
 * URLhaus focuses on URLs distributing malware; a no-match does not mean safe.
 */
public final class UrlhausReputation {
    private static final String ENDPOINT = "https://urlhaus-api.abuse.ch/v1/url/";
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private UrlhausReputation() {}

    public static final class Result {
        public final boolean listed;
        public final String status;
        public final String details;
        private Result(boolean listed, String status, String details) {
            this.listed = listed;
            this.status = status;
            this.details = details;
        }
    }

    public static Result lookup(String value, String authKey) {
        final String url = validateHttpsUrl(value);
        if (url == null) {
            return new Result(false, "INVALID_URL",
                    "Only valid HTTPS URLs without embedded username/password are accepted.");
        }
        if (authKey == null || authKey.trim().isEmpty()) {
            return new Result(false, "NOT_CONFIGURED", "URLhaus reputation requires an abuse.ch Auth-Key.");
        }

        HttpURLConnection connection = null;
        try {
            String body = "url=" + URLEncoder.encode(url, "UTF-8");
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Auth-Key", authKey.trim());
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] requestBytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
            }

            int code = connection.getResponseCode();
            InputStream response = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (response == null) {
                return new Result(false, "SERVICE_UNAVAILABLE", "URLhaus returned HTTP " + code + ".");
            }
            byte[] payload;
            try (InputStream input = response; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_RESPONSE_BYTES) {
                        return new Result(false, "SERVICE_RESPONSE_TOO_LARGE", "URLhaus response exceeded the local safety limit.");
                    }
                    output.write(buffer, 0, count);
                }
                payload = output.toByteArray();
            }
            if (code < 200 || code >= 300) {
                return new Result(false, "SERVICE_UNAVAILABLE", "URLhaus request failed with HTTP " + code + ".");
            }

            JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            String queryStatus = json.optString("query_status", "");
            if ("no_results".equalsIgnoreCase(queryStatus)) {
                return new Result(false, "NO_MATCH",
                        "URLhaus has no exact record for this URL. This is not a safety verdict and does not rule out phishing, scams, or other threats.");
            }
            if ("ok".equalsIgnoreCase(queryStatus)) {
                String urlStatus = json.optString("url_status", "unknown");
                String host = json.optString("host", "");
                String threat = json.optString("threat", "");
                String reference = json.optString("urlhaus_reference", "");
                String added = json.optString("date_added", "");
                JSONObject blacklists = json.optJSONObject("blacklists");
                JSONArray tags = json.optJSONArray("tags");

                StringBuilder details = new StringBuilder("URLhaus has an exact URL record associated with malware distribution.");
                details.append("\nReported URL status: ").append(safeText(urlStatus));
                if (!host.isEmpty()) details.append("\nReported host: ").append(safeText(host));
                if (!threat.isEmpty()) details.append("\nThreat category: ").append(safeText(threat));
                if (!added.isEmpty() && !"null".equalsIgnoreCase(added)) details.append("\nDate added (UTC): ").append(safeText(added));
                if (blacklists != null) {
                    String spamhaus = blacklists.optString("spamhaus_dbl", "");
                    String surbl = blacklists.optString("surbl", "");
                    if (!spamhaus.isEmpty()) details.append("\nSpamhaus DBL: ").append(safeText(spamhaus));
                    if (!surbl.isEmpty()) details.append("\nSURBL: ").append(safeText(surbl));
                }
                if (tags != null && tags.length() > 0) {
                    StringBuilder tagText = new StringBuilder();
                    for (int i = 0; i < Math.min(tags.length(), 12); i++) {
                        String tag = tags.optString(i, "");
                        if (!tag.isEmpty()) {
                            if (tagText.length() > 0) tagText.append(", ");
                            tagText.append(safeText(tag));
                        }
                    }
                    if (tagText.length() > 0) details.append("\nTags: ").append(tagText);
                }
                if (!reference.isEmpty()) details.append("\nURLhaus record: ").append(safeText(reference));
                details.append("\nURLhaus tracks malware distribution; its match is a warning to avoid the URL. This service is not a complete phishing/scam detector.");
                return new Result(true, "KNOWN_MALWARE_URL", details.toString());
            }
            return new Result(false, "SERVICE_RESPONSE",
                    "URLhaus did not return a usable reputation result (" +
                            safeText(queryStatus.isEmpty() ? "unknown response" : queryStatus) + ").");
        } catch (Exception error) {
            return new Result(false, "SERVICE_UNAVAILABLE",
                    "URLhaus lookup failed (" + error.getClass().getSimpleName() + "). Local URL heuristics remain unchanged.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String validateHttpsUrl(String value) {
        if (value == null) return null;
        String input = value.trim();
        if (input.length() < 9 || input.length() > 4096) return null;
        try {
            URI uri = new URI(input);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null || uri.getHost().isEmpty() ||
                    uri.getUserInfo() != null) return null;
            // Fragments are local to the browser and are not sent to a web server.
            // Exclude them from the reputation query as well.
            int fragment = input.indexOf('#');
            String clean = fragment >= 0 ? input.substring(0, fragment) : input;
            return clean.length() <= 4096 ? clean : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    private static String safeText(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 180 ? clean.substring(0, 180) : clean;
    }
}
