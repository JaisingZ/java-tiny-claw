package com.jaising.agent.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * LM Studio 配置
 * 从 properties 文件读取模型连接参数
 */
public final class LmStudioConfig {

    public static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";

    private final String baseUrl;
    private final String model;

    public LmStudioConfig(String baseUrl, String model) {
        this.baseUrl = trimToDefault(baseUrl, DEFAULT_BASE_URL);
        this.model = trimToEmpty(model);
    }

    public static LmStudioConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent config: " + path, ex);
        }
        return new LmStudioConfig(
                properties.getProperty("lmstudio.baseUrl"),
                properties.getProperty("lmstudio.model"));
    }

    public static LmStudioConfig loadDefault() {
        String configPath = System.getProperty("agent.config");
        if (configPath != null && !configPath.trim().isEmpty()) {
            return load(Path.of(configPath));
        }
        Path localConfig = Path.of("agent.properties");
        if (Files.exists(localConfig)) {
            return load(localConfig);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = LmStudioConfig.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("agent.properties not found");
            }
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath agent.properties", ex);
        }
        return new LmStudioConfig(
                properties.getProperty("lmstudio.baseUrl"),
                properties.getProperty("lmstudio.model"));
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
