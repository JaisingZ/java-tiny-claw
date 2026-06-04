package io.github.tinyclaw.agent.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Telegram 随应用启动的轻量配置。
 */
final class TelegramStartupConfig {

    private static final String PROPERTY_ENABLED = "telegram.webhook.enabled";

    private final boolean enabled;

    private TelegramStartupConfig(boolean enabled) {
        this.enabled = enabled;
    }

    static TelegramStartupConfig from(Map<String, String> values) {
        String value = optional(values, PROPERTY_ENABLED, "false");
        return new TelegramStartupConfig(parseBoolean(value, PROPERTY_ENABLED));
    }

    static TelegramStartupConfig load(Path path) {
        return from(loadProperties(path));
    }

    static TelegramStartupConfig loadDefault() {
        Map<String, String> values = loadDefaultProperties();
        return from(values);
    }

    boolean enabled() {
        return enabled;
    }

    private static Map<String, String> loadDefaultProperties() {
        String configPath = System.getProperty("agent.config");
        if (hasText(configPath)) {
            return loadProperties(Path.of(configPath));
        }

        Path localConfig = Path.of("agent.properties");
        if (Files.exists(localConfig)) {
            return loadProperties(localConfig);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = TelegramStartupConfig.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath agent.properties", ex);
        }
        return toMap(properties);
    }

    private static Map<String, String> loadProperties(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent config: " + path, ex);
        }
        return toMap(properties);
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> values = new HashMap<String, String>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return values;
    }

    private static String optional(Map<String, String> values, String primaryKey, String defaultValue) {
        String value = values.get(primaryKey);
        if (hasText(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    private static boolean parseBoolean(String value, String key) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalStateException("Invalid boolean for " + key + ": " + value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
