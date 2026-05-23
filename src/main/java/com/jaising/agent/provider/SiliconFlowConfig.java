package com.jaising.agent.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * SiliconFlow 配置
 * 从 properties 文件读取模型连接参数
 */
public final class SiliconFlowConfig {

    public static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public SiliconFlowConfig(String apiKey, String baseUrl, String model) {
        this.apiKey = trimToEmpty(apiKey);
        this.baseUrl = trimToDefault(baseUrl, DEFAULT_BASE_URL);
        this.model = trimToEmpty(model);
    }

    public static SiliconFlowConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent config: " + path, ex);
        }
        return new SiliconFlowConfig(
                properties.getProperty("siliconflow.apiKey"),
                properties.getProperty("siliconflow.baseUrl"),
                properties.getProperty("siliconflow.model"));
    }

    public static SiliconFlowConfig loadDefault() {
        String configPath = System.getProperty("agent.config");
        if (configPath != null && !configPath.trim().isEmpty()) {
            return load(Path.of(configPath));
        }
        Path localConfig = Path.of("agent.properties");
        if (Files.exists(localConfig)) {
            return load(localConfig);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = SiliconFlowConfig.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("agent.properties not found");
            }
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath agent.properties", ex);
        }
        return new SiliconFlowConfig(
                properties.getProperty("siliconflow.apiKey"),
                properties.getProperty("siliconflow.baseUrl"),
                properties.getProperty("siliconflow.model"));
    }

    public String apiKey() {
        return apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
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
