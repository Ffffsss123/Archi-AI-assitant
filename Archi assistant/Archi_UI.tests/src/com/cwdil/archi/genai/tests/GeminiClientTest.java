package com.cwdil.archi.genai.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.http.HttpClient;

import org.junit.Test;

import com.cwdil.archi.genai.ui.explain.GeminiClient;

public class GeminiClientTest {

    @Test
    public void generateContentReturnsParsedText() throws Exception {
        try(TestGeminiServer server = new TestGeminiServer()) {
            server.setResponse(200, "{\"text\": \"Hello\"}");

            GeminiClient client = new GeminiClient(HttpClient.newHttpClient(), "test-key", server.getEndpointUri());
            String result = client.generateContent("prompt");

            assertEquals("Hello", result);
            assertEquals("test-key", server.getLastApiKey());
            assertTrue(server.getLastRequestBody().contains("\"text\": \"prompt\""));
        }
    }

    @Test
    public void generateContentThrowsOnNon2xx() throws Exception {
        try(TestGeminiServer server = new TestGeminiServer()) {
            server.setResponse(401, "Unauthorized");

            GeminiClient client = new GeminiClient(HttpClient.newHttpClient(), "test-key", server.getEndpointUri());
            try {
                client.generateContent("prompt");
                fail("Expected RuntimeException");
            }
            catch(RuntimeException ex) {
                assertTrue(ex.getMessage().contains("HTTP 401"));
            }
        }
    }
}
