package com.cwdil.archi.genai.config;

import java.net.URI;

public final class OpenAIConfig {

    public static final String API_KEY_KEY = "OPENAI_API_KEY";
    public static final String COMMON_API_KEY = "AI_API_KEY";
    public static final String BASE_URL_KEY = "OPENAI_BASE_URL";
    public static final String MODEL_KEY = "OPENAI_MODEL";
    public static final String ENDPOINT_URL_KEY = "OPENAI_ENDPOINT_URL";

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-5.2";

    private OpenAIConfig() {
    }

    public static String resolveApiKey() {
        return SecretProvider.getWithFallback(API_KEY_KEY, COMMON_API_KEY);
    }

    public static URI resolveEndpointUri() {
        String endpoint = ConfigProvider.get(ENDPOINT_URL_KEY, null);
        if (endpoint != null && !endpoint.isBlank()) {
            return URI.create(endpoint);
        }

        String baseUrl = ConfigProvider.get(BASE_URL_KEY, DEFAULT_BASE_URL);
        baseUrl = trimTrailingSlash(baseUrl);

        return URI.create(baseUrl + "/responses");
    }

    public static String resolveModel() {
        return ConfigProvider.get(MODEL_KEY, DEFAULT_MODEL);
    }

    public static String requireApiKey(String apiKey) {
        String resolved = apiKey;
        if (resolved == null || resolved.isBlank()) {
            resolved = resolveApiKey();
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException(SecretProvider.missingKeyMessage(API_KEY_KEY, COMMON_API_KEY));
        }
        return resolved;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
