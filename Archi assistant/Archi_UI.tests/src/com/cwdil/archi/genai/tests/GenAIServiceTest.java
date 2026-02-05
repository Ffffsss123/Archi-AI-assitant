package com.cwdil.archi.genai.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.cwdil.archi.genai.services.GenAIService;

public class GenAIServiceTest {

    @Test
    public void sendPromptAsyncReturnsParsedText() throws Exception {
        try(TestOpenAIServer server = new TestOpenAIServer()) {
            server.setResponse(200, "{\"output_text\": \"Hello\"}");

            GenAIService service = new GenAIService(HttpClient.newHttpClient(), "test-key", server.getEndpointUri(), "gpt-5.2");
            String result = service.sendPromptAsync("hi").get(2, TimeUnit.SECONDS);

            assertEquals("Hello", result);
            assertEquals("Bearer test-key", server.getLastAuthorization());
            assertTrue(server.getLastRequestBody().contains("\"input\": \"hi\""));
        }
    }

    @Test
    public void sendPromptAsyncParsesOutputArrayText() throws Exception {
        try(TestOpenAIServer server = new TestOpenAIServer()) {
            server.setResponse(200, "{\"output\": [{\"content\": [{\"type\": \"output_text\", \"text\": \"Hi\"}]}]}");

            GenAIService service = new GenAIService(HttpClient.newHttpClient(), "test-key", server.getEndpointUri(), "gpt-5.2");
            String result = service.sendPromptAsync("hi").get(2, TimeUnit.SECONDS);

            assertEquals("Hi", result);
        }
    }

    @Test
    public void sendPromptAsyncReturnsErrorTextOnNon2xx() throws Exception {
        try(TestOpenAIServer server = new TestOpenAIServer()) {
            server.setResponse(400, "Bad request");

            GenAIService service = new GenAIService(HttpClient.newHttpClient(), "test-key", server.getEndpointUri(), "gpt-5.2");
            String result = service.sendPromptAsync("hi").get(2, TimeUnit.SECONDS);

            assertTrue(result.contains("Status 400"));
        }
    }
}
