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
 * Small HTTPS client for Hari AI. API keys and raw response bodies are never logged.
 * Stable low-cost models are tried in order only when the requested model is unavailable.
 */
final class HariAiClient {
    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODEL_IDS = {
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash"
    };
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private HariAiClient() {}

    static String generateReply(String apiKey, JSONArray contents) throws IOException {
        validateInput(apiKey, contents);
        ModelUnavailableException lastUnavailable = null;
        for (String modelId : MODEL_IDS) {
            try {
                return generateReplyForModel(apiKey.trim(), contents, modelId);
            } catch (ModelUnavailableException unavailable) {
                lastUnavailable = unavailable;
            }
        }
        throw new ClientException(
                "Hari AI could not access an available model for this API key. Check the key's API access in Google AI Studio, then try again.",
                lastUnavailable);
    }

    /**
     * Explicit settings action to verify that a saved/proposed key can make one small request.
     * This sends a short prompt and uses API quota, so it is never run automatically.
     */
    static void testConnection(String apiKey) throws IOException {
        JSONArray contents = new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text",
                        "Reply with only the word OK."))));
        generateReply(apiKey, contents);
    }

    static String endpointForModel(String modelId) {
        if (modelId == null || !modelId.matches("gemini-[a-zA-Z0-9.-]+")) {
            throw new IllegalArgumentException("Invalid model identifier");
        }
        return API_BASE + modelId + ":generateContent";
    }

    static String httpStatusMessage(int status) {
        if (status == 400) {
            return "The AI service rejected the request. Check the key's API access and try a shorter question.";
        }
        if (status == 401) {
            return "The API key is invalid or expired. Create a valid key in Google AI Studio and save it in Hari AI settings.";
        }
        if (status == 402) {
            return "The AI service requires available billing or credits for this request. Check the project's usage and billing settings.";
        }
        if (status == 403) {
            return "The API key or project is not allowed to use this service. Check key restrictions and API access in Google AI Studio.";
        }
        if (status == 404) {
            return "The requested AI model is unavailable for this key.";
        }
        if (status == 429) {
            return "The AI usage limit was reached. Check the project's quota or try again later.";
        }
        if (status >= 500) {
            return "The AI service is temporarily unavailable. Try again later.";
        }
        return "Hari AI request failed (HTTP " + status + "). Please try again.";
    }

    private static void validateInput(String apiKey, JSONArray contents) throws ClientException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ClientException("Add your API key in Hari AI settings.");
        }
        if (contents == null || contents.length() == 0) {
            throw new ClientException("Enter a question before sending.");
        }
    }

    private static String generateReplyForModel(String apiKey, JSONArray contents, String modelId)
            throws IOException {
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

            connection = (HttpsURLConnection) new URL(endpointForModel(modelId)).openConnection();
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
                throw new ClientException(
                        "Could not reach Hari AI. Check your internet connection and try again.", error);
            }

            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = responseStream == null ? "" : readBounded(responseStream);
            if (status == 404) {
                // A model may be unavailable for a project or may change over time. The caller
                // retries the next currently supported stable model; do not retry other 4xx errors.
                throw new ModelUnavailableException();
            }
            if (status < 200 || status >= 300) {
                throw new ClientException(httpStatusMessage(status));
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
                throw new ClientException(
                        "Hari AI could not answer that message. Try rephrasing it or asking a different question.");
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
        } catch (ModelUnavailableException error) {
            throw error;
        } catch (ClientException error) {
            throw error;
        } catch (JSONException error) {
            throw new ClientException("Could not prepare the request. Please try again.", error);
        } catch (IOException error) {
            throw new ClientException(
                    "Could not reach Hari AI. Check your internet connection and try again.", error);
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

    private static class ClientException extends IOException {
        private static final long serialVersionUID = 1L;
        ClientException(String message) { super(message); }
        ClientException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class ModelUnavailableException extends ClientException {
        private static final long serialVersionUID = 1L;
        ModelUnavailableException() {
            super("The requested AI model is unavailable.");
        }
    }
}
