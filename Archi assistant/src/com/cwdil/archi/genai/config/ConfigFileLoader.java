package com.cwdil.archi.genai.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

final class ConfigFileLoader {

    static final String CONFIG_PATH_KEY = "ARCHI_AI_CONFIG";
    private static volatile Properties cached;
    private static volatile boolean loaded;

    private ConfigFileLoader() {
    }

    static Properties getProperties() {
        if (loaded) {
            return cached;
        }
        synchronized (ConfigFileLoader.class) {
            if (loaded) {
                return cached;
            }
            cached = loadProperties();
            loaded = true;
            return cached;
        }
    }

    static String getProperty(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Properties props = getProperties();
        if (props == null) {
            return null;
        }
        String value = props.getProperty(key);
        return (value != null && !value.isBlank()) ? value : null;
    }

    private static Properties loadProperties() {
        String pathValue = System.getProperty(CONFIG_PATH_KEY);
        if (pathValue == null || pathValue.isBlank()) {
            return null;
        }
        Path path = Paths.get(pathValue.trim());
        if (!path.isAbsolute()) {
            throw new IllegalStateException("ARCHI_AI_CONFIG must be an absolute path: " + pathValue);
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("ARCHI_AI_CONFIG file not found: " + pathValue);
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read ARCHI_AI_CONFIG: " + pathValue, e);
        }
        return props;
    }
}
