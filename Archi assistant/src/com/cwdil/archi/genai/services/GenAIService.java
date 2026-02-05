package com.cwdil.archi.genai.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cwdil.archi.genai.config.OpenAIConfig;
import com.cwdil.archi.genai.util.SimpleJsonParser;
import com.cwdil.archi.genai.util.SimpleJsonParser.ParseException;

/**
 * Service for handling OpenAI Responses API interactions.
 */
public class GenAIService {

    private final HttpClient httpClient;
    private final String apiKey;
    private final URI endpoint;
    private final String model;

    public GenAIService() {
        this(HttpClient.newHttpClient(),
                OpenAIConfig.resolveApiKey(),
                OpenAIConfig.resolveEndpointUri(),
                OpenAIConfig.resolveModel());
    }

    public GenAIService(HttpClient httpClient, String apiKey, URI endpoint) {
        this(httpClient, apiKey, endpoint, OpenAIConfig.resolveModel());
    }

    public GenAIService(HttpClient httpClient, String apiKey, URI endpoint, String model) {
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.endpoint = endpoint != null ? endpoint : OpenAIConfig.resolveEndpointUri();
        this.model = model != null ? model : OpenAIConfig.resolveModel();
    }

    /**
     * Sends a prompt to the OpenAI Responses API and returns the response asynchronously.
     */
    public CompletableFuture<String> sendPromptAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendRequestToOpenAI(prompt);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error calling OpenAI API: " + e.getMessage());
            }
        });
    }

    private String sendRequestToOpenAI(String prompt) throws Exception {
        String resolvedApiKey = OpenAIConfig.requireApiKey(
                apiKey != null ? apiKey : OpenAIConfig.resolveApiKey());
        URI resolvedEndpoint = endpoint != null ? endpoint : OpenAIConfig.resolveEndpointUri();
        String resolvedModel = (model != null && !model.isBlank())
                ? model
                : OpenAIConfig.resolveModel();

        // Responses API payload: { "model": "...", "input": "..." }
        String jsonPayload = "{"
                + "\"model\": \"" + escapeJson(resolvedModel) + "\","
                + "\"input\": \"" + escapeJson(prompt) + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvedEndpoint)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resolvedApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 == 2) {
            return extractTextFromResponse(response.body());
        } else {
            return "Error from OpenAI API (Status " + response.statusCode() + "): " + response.body();
        }
    }

    // Simple JSON escaping
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    // Extract the "output_text" or content text from Responses API response
    private String extractTextFromResponse(String json) {
        try {
            String extracted = extractFromResponsesJson(json);
            if(extracted != null && !extracted.isBlank()) {
                return extracted;
            }
            return "Could not parse response: " + json;
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }

    private String extractFromResponsesJson(String json) throws ParseException {
        if(json == null || json.isBlank()) {
            return null;
        }
        Object root = SimpleJsonParser.parse(json);
        if(!(root instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)root;

        String outputText = asString(map.get("output_text"));
        if(outputText != null && !outputText.isBlank()) {
            return outputText;
        }

        Object output = map.get("output");
        if(output instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> outputs = (List<Object>)output;
            List<String> parts = new ArrayList<>();
            for(Object item : outputs) {
                if(!(item instanceof Map)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>)item;
                Object content = itemMap.get("content");
                if(content instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> contents = (List<Object>)content;
                    for(Object contentItem : contents) {
                        if(!(contentItem instanceof Map)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> contentMap = (Map<String, Object>)contentItem;
                        String type = asString(contentMap.get("type"));
                        if("output_text".equals(type)) {
                            String text = asString(contentMap.get("text"));
                            if(text != null && !text.isBlank()) {
                                parts.add(text);
                            }
                        }
                    }
                }
            }
            if(!parts.isEmpty()) {
                return String.join("\n", parts);
            }
        }

        return null;
    }

    private String asString(Object value) {
        if(value instanceof String) {
            return (String)value;
        }
        if(value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
