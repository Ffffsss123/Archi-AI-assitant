package com.cwdil.archi.genai.config;

import java.net.URI;
import java.util.Locale;

public final class GeminiApiConfig {

    public static final String EXPLAIN_API_KEY = "EXPLAIN_API_KEY";
    public static final String GENAI_API_KEY = "GENAI_API_KEY";
    public static final String COMMON_API_KEY = "AI_API_KEY";
    public static final String BASE_URL_KEY = "GEMINI_BASE_URL";
    public static final String MODEL_KEY = "GEMINI_MODEL";
    public static final String ENDPOINT_URL_KEY = "GEMINI_ENDPOINT_URL";

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    private GeminiApiConfig() {
    }

    public static String resolveApiKey() {
        String value = SecretProvider.getWithFallback(EXPLAIN_API_KEY, GENAI_API_KEY);
        if (value == null || value.isBlank()) {
            value = SecretProvider.getOptional(COMMON_API_KEY);
        }
        return value;
    }

    public static URI resolveEndpointUri() {
        String endpoint = ConfigProvider.get(ENDPOINT_URL_KEY, null);
        if (endpoint != null && !endpoint.isBlank()) {
            return URI.create(endpoint);
        }

        String baseUrl = ConfigProvider.get(BASE_URL_KEY, DEFAULT_BASE_URL);
        baseUrl = normalizeBaseUrl(baseUrl);

        String model = ConfigProvider.get(MODEL_KEY, DEFAULT_MODEL);

        return URI.create(baseUrl + "/models/" + model + ":generateContent");
    }

    public static String requireApiKey(String apiKey) {
        String resolved = apiKey;
        if (resolved == null || resolved.isBlank()) {
            resolved = resolveApiKey();
        }
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException(SecretProvider.missingKeyMessage(
                    EXPLAIN_API_KEY, GENAI_API_KEY, COMMON_API_KEY));
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

    private static String normalizeBaseUrl(String baseUrl) {
        baseUrl = trimTrailingSlash(baseUrl);
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String lower = baseUrl.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/v1") || lower.endsWith("/v1beta")) {
            return baseUrl;
        }
        return baseUrl + "/v1beta";
    }
}
