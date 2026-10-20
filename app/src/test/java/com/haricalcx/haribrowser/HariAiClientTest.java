package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HariAiClientTest {
    @Test
    public void usesDocumentedGenerateContentEndpointShape() {
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
                HariAiClient.endpointForModel("gemini-3.5-flash-lite"));
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent",
                HariAiClient.endpointForModel("gemini-3.1-flash-lite"));
    }

    @Test
    public void endpointBuilderRejectsMalformedModelIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> HariAiClient.endpointForModel("https://example.com/"));
        assertThrows(IllegalArgumentException.class,
                () -> HariAiClient.endpointForModel("../unauthorized"));
    }

    @Test
    public void explainsCommonApiKeyAndQuotaErrors() {
        assertTrue(HariAiClient.httpStatusMessage(400).contains("rejected the request"));
        assertTrue(HariAiClient.httpStatusMessage(401).contains("API key is invalid"));
        assertTrue(HariAiClient.httpStatusMessage(403).contains("not allowed"));
        assertTrue(HariAiClient.httpStatusMessage(402).contains("billing or credits"));
        assertTrue(HariAiClient.httpStatusMessage(404).contains("model is unavailable"));
        assertTrue(HariAiClient.httpStatusMessage(429).contains("usage limit"));
        assertTrue(HariAiClient.httpStatusMessage(503).contains("temporarily unavailable"));
    }

    @Test
    public void unexpectedHttpStatusesDoNotExposeResponseBodiesOrSecrets() {
        String message = HariAiClient.httpStatusMessage(418);
        assertTrue(message.contains("HTTP 418"));
        assertTrue(!message.contains("AIza"));
    }
}
