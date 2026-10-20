package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Small HTTPS client for Hari AI. API keys and responses are never written to logs.
 */
final class HariAiClient {
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private HariAiClient() {}

    static String generateReply(String apiKey, JSONArray contents) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ClientException("Add your API key in Hari AI settings.");
        }
        if (contents == null || contents.length() == 0) {
            throw new ClientException("Enter a question before sending.");
        }

        HttpsURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            JSONObject instruction = new JSONObject();
            JSONArray instructionParts = new JSONArray();
            instructionParts.put(new JSONObject().put("text",
                    "You are Hari AI, a helpful, accurate, friendly assistant. Help with education, maths, science, coding, history, general knowledge, writing and creative tasks. Explain reasoning clearly when useful. If you are uncertain, say so. Do not claim to have browsed the web or performed actions you have not performed."));
            instruction.put("parts", instructionParts);
            body.put("systemInstruction", instruction);
            body.put("contents", contents);
            body.put("generationConfig", new JSONObject()
                    .put("temperature", 0.7)
                    .put("maxOutputTokens", 2048));

            connection = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-goog-api-key", apiKey);

            byte[] requestBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }

            final int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException error) {
                throw new ClientException("Could not connect to Hari AI. Check your internet connection.");
            }

            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = responseStream == null ? "" : readBounded(responseStream);
            if (status < 200 || status >= 300) {
                throw statusError(status);
            }
            if (responseText.isEmpty()) {
                throw new ClientException("Hari AI returned an empty reply. Try again.");
            }

            final JSONObject response;
            try {
                response = new JSONObject(responseText);
            } catch (JSONException error) {
                throw new ClientException("Hari AI returned an unreadable reply. Try again.");
            }

            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                throw new ClientException("This message could not be answered. Try rephrasing it.");
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject responseContent = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = responseContent == null ? null : responseContent.optJSONArray("parts");
            if (parts == null) {
                throw new ClientException("Hari AI could not prepare a text reply. Try again.");
            }

            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;
                String fragment = part.optString("text", "");
                if (!fragment.isEmpty()) {
                    if (text.length() > 0) text.append('\n');
                    text.append(fragment);
                }
            }
            if (text.toString().trim().isEmpty()) {
                throw new ClientException("Hari AI returned no text. Try rephrasing your question.");
            }
            return text.toString().trim();
        } catch (ClientException error) {
            throw error;
        } catch (JSONException error) {
            throw new ClientException("Could not prepare the request. Please try again.");
        } catch (IOException error) {
            throw new ClientException("Could not connect to Hari AI. Check your internet connection and try again.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new ClientException("The reply was too large to process. Try again.");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static ClientException statusError(int status) {
        if (status == 400) {
            return new ClientException("The request was not accepted. Try a shorter message or check Hari AI settings.");
        }
        if (status == 401 || status == 403) {
            return new ClientException("The API key was rejected or restricted. Check Hari AI settings.");
        }
        if (status == 429) {
            return new ClientException("The usage limit was reached. Check your API quota or try again later.");
        }
        if (status >= 500) {
            return new ClientException("The AI service is temporarily unavailable. Try again later.");
        }
        return new ClientException("Hari AI request failed (HTTP " + status + "). Please try again.");
    }

    private static final class ClientException extends IOException {
        private static final long serialVersionUID = 1L;
        ClientException(String message) { super(message); }
    }
}
