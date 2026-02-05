package com.cwdil.archi.genai.config;

public final class SecretProvider {

    private SecretProvider() {
    }

    public static String getRequired(String keyName) {
        String value = getOptional(keyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(missingKeyMessage(keyName));
        }
        return value;
    }

    public static String getOptional(String keyName) {
        if (keyName == null || keyName.isBlank()) {
            return null;
        }
        String value = readSystemProperty(keyName);
        if (isBlank(value)) {
            value = readEnv(keyName);
        }
        if (isBlank(value)) {
            value = ConfigFileLoader.getProperty(keyName);
        }
        return isBlank(value) ? null : value;
    }

    public static String getWithFallback(String specificKey, String commonKey) {
        String value = getOptional(specificKey);
        if (isBlank(value)) {
            value = getOptional(commonKey);
        }
        return isBlank(value) ? null : value;
    }

    public static String missingKeyMessage(String primaryKey, String... fallbackKeys) {
        StringBuilder sb = new StringBuilder();
        sb.append("Missing ").append(primaryKey);
        String fallbacks = formatFallbacks(fallbackKeys);
        if (!fallbacks.isEmpty()) {
            sb.append(" (or ").append(fallbacks).append(")");
        }
        sb.append(". Configure via Eclipse VM arguments, e.g. -D")
          .append(primaryKey).append("=YOUR_KEY");
        if (!fallbacks.isEmpty()) {
            for (String key : fallbackKeys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                sb.append(" or -D").append(key).append("=YOUR_KEY");
            }
        }
        sb.append(". Optional config file: -D")
          .append(ConfigFileLoader.CONFIG_PATH_KEY)
          .append("=C:\\path\\ai.properties");
        return sb.toString();
    }

    private static String formatFallbacks(String... fallbackKeys) {
        if (fallbackKeys == null || fallbackKeys.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : fallbackKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" or ");
            }
            sb.append(key);
        }
        return sb.toString();
    }

    private static String readSystemProperty(String key) {
        return System.getProperty(key);
    }

    private static String readEnv(String key) {
        return System.getenv(key);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
