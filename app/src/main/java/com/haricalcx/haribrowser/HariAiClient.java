package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/**
 * Small HTTPS client for Hari AI. API keys and raw response bodies are never logged.
 * Stable low-cost models are tried in order only when the requested model is unavailable.
 */
final class HariAiClient {
    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String API_MODELS_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String[] MODEL_IDS = {
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.6-flash"
    };
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private HariAiClient() {}

    static String generateReply(String apiKey, JSONArray contents) throws IOException {
        validateInput(apiKey, contents);
        ModelUnavailableException lastUnavailable = null;
        Set<String> attempted = new HashSet<>();
        for (String modelId : MODEL_IDS) {
            attempted.add(modelId);
            try {
                return generateReplyForModel(apiKey.trim(), contents, modelId);
            } catch (ModelUnavailableException unavailable) {
                lastUnavailable = unavailable;
            }
        }

        // Model availability changes over time and can vary by API key/project.
        // Only perform discovery after every known low-cost candidate returned 404.
        final List<String> discovered;
        try {
            discovered = listAvailableFlashModels(apiKey.trim());
        } catch (IOException discoveryError) {
            throw new ClientException(
                    "Hari AI could not access an available model. Check the key's API access in Google AI Studio, then try again.",
                    lastUnavailable == null ? discoveryError : lastUnavailable);
        }
        for (String modelId : discovered) {
            if (!attempted.add(modelId)) continue;
            try {
                return generateReplyForModel(apiKey.trim(), contents, modelId);
            } catch (ModelUnavailableException unavailable) {
                lastUnavailable = unavailable;
            }
        }
        throw new ClientException(
                "Hari AI could not access an available text-generation model for this API key. Check the key's API access in Google AI Studio, then try again.",
                lastUnavailable);
    }

    /**
     * Explicit settings action to verify that a saved/proposed key can make one small request.
     * This sends a short prompt and uses API quota, so it is never run automatically.
     */
    static void testConnection(String apiKey) throws IOException {
        final JSONArray contents;
        try {
            JSONObject part = new JSONObject().put("text", "Reply with only the word OK.");
            JSONObject message = new JSONObject()
                    .put("role", "user")
                    .put("parts", new JSONArray().put(part));
            contents = new JSONArray().put(message);
        } catch (JSONException error) {
            throw new ClientException("Could not prepare the AI connection test.", error);
        }
        generateReply(apiKey, contents);
    }

    static String modelsEndpoint() {
        return API_MODELS_ENDPOINT;
    }

    /**
     * Parse models returned for the current API key. Only standard Gemini Flash
     * text-generation IDs are accepted; image, audio, embedding and other model
     * families are intentionally excluded from this text-chat client.
     */
    static List<String> parseAvailableFlashModels(String responseText) throws ClientException {
        final JSONObject response;
        try {
            response = new JSONObject(responseText);
        } catch (JSONException error) {
            throw new ClientException("Hari AI returned an unreadable model list.", error);
        }
        JSONArray models = response.optJSONArray("models");
        if (models == null) return Collections.emptyList();

        List<String> available = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            String name = model.optString("name", "");
            if (!name.startsWith("models/")) continue;
            String modelId = name.substring("models/".length());
            if (!isTextFlashModelId(modelId)) continue;
            JSONArray methods = model.optJSONArray("supportedGenerationMethods");
            if (methods == null) continue;
            boolean supportsGenerateContent = false;
            for (int j = 0; j < methods.length(); j++) {
                if ("generateContent".equals(methods.optString(j))) {
                    supportsGenerateContent = true;
                    break;
                }
            }
            if (supportsGenerateContent && !available.contains(modelId)) available.add(modelId);
        }
        available.sort((left, right) -> {
            int priority = Integer.compare(modelPriority(left), modelPriority(right));
            return priority != 0 ? priority : left.compareTo(right);
        });
        return Collections.unmodifiableList(available);
    }

    private static boolean isTextFlashModelId(String modelId) {
        return modelId != null
                && modelId.matches("gemini-[0-9]+(\\\\.[0-9]+)*-flash(-lite)?");
    }

    private static int modelPriority(String modelId) {
        if ("gemini-3.5-flash-lite".equals(modelId)) return 0;
        if ("gemini-3.1-flash-lite".equals(modelId)) return 1;
        if ("gemini-3.8-flash".equals(modelId)) return 2;
        if ("gemini-3.6-flash".equals(modelId)) return 3;
        if ("gemini-2.5-flash-lite".equals(modelId)) return 4;
        if ("gemini-2.5-flash".equals(modelId)) return 5;
        return 10;
    }

    private static List<String> listAvailableFlashModels(String apiKey) throws IOException {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(modelsEndpoint()).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-goog-api-key", apiKey);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = stream == null ? "" : readBounded(stream);
            if (status < 200 || status >= 300) {
                throw new ClientException(httpStatusMessage(status));
            }
            return parseAvailableFlashModels(responseText);
        } finally {
            if (connection != null) connection.disconnect();
        }
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
                    "You are Hari AI, a helpful, accurate, friendly assistant. Help with education, maths, science, coding, history, general knowledge, writing and creative tasks. Explain reasoning clearly when useful. If you are uncertain, say so. Do not claim to have browsed the web or performed actions you have not performed. When useful, adapt examples to India, use ₹ (INR), Indian numbering such as lakh and crore, metric units, and Indian Standard Time. Reply in the language and script the user uses when supported, including Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi and Urdu. Do not assume the user's state, religion, caste, or political views. Ask which state or jurisdiction applies when local rules matter. For current government, legal, tax, or benefits questions, distinguish general guidance from verified current official information."));
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
