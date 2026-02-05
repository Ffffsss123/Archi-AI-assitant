package com.cwdil.archi.genai.config;

public final class ConfigProvider {

    private ConfigProvider() {
    }

    public static String get(String keyName, String defaultValue) {
        if (keyName == null || keyName.isBlank()) {
            return defaultValue;
        }
        String value = System.getProperty(keyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(keyName);
        }
        if (value == null || value.isBlank()) {
            value = ConfigFileLoader.getProperty(keyName);
        }
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
