package com.cwdil.archi.genai.ui.explain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.cwdil.archi.genai.config.GeminiApiConfig;

public class GeminiClient {

    private final HttpClient client;
    private final String apiKey;
    private final URI endpoint;

    public GeminiClient() {
        this(HttpClient.newHttpClient(),
                GeminiApiConfig.resolveApiKey(),
                GeminiApiConfig.resolveEndpointUri());
    }

    public GeminiClient(HttpClient client, String apiKey, URI endpoint) {
        this.client = client != null ? client : HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.endpoint = endpoint != null ? endpoint : GeminiApiConfig.resolveEndpointUri();
    }

    public String generateContent(String promptText) throws Exception {
        String resolvedApiKey = GeminiApiConfig.requireApiKey(
                apiKey != null ? apiKey : GeminiApiConfig.resolveApiKey());
        URI resolvedEndpoint = endpoint != null ? endpoint : GeminiApiConfig.resolveEndpointUri();

        String escaped = jsonEscape(promptText);

        String requestBody = """
            {
              \"contents\": [
                {
                  \"parts\": [
                    { \"text\": \"%s\" }
                  ]
                }
              ]
            }
            """.formatted(escaped);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(resolvedEndpoint)
                .header("Content-Type", "application/json")
                .header("X-goog-api-key", resolvedApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if(response.statusCode() / 100 != 2) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        String body = response.body();
        String resultText = extractFirstTextFromGeminiResponse(body);
        if(resultText == null) {
            throw new RuntimeException("No 'text' field found in Gemini response.");
        }

        return resultText.replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String jsonEscape(String s) {
        if(s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch(c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if(c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                    }
                    else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String extractFirstTextFromGeminiResponse(String body) {
        if(body == null) {
            return null;
        }

        int idx = body.indexOf("\"text\"");
        if(idx == -1) {
            return null;
        }

        idx = body.indexOf(':', idx);
        if(idx == -1) {
            return null;
        }

        int start = body.indexOf('"', idx + 1);
        if(start == -1) {
            return null;
        }
        start++;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for(int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if(escaped) {
                sb.append(c);
                escaped = false;
            }
            else {
                if(c == '\\') {
                    sb.append(c);
                    escaped = true;
                }
                else if(c == '"') {
                    break;
                }
                else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
