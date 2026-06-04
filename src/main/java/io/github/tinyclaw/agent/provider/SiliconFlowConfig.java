package io.github.tinyclaw.agent.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * SiliconFlow 连接配置。
 * 从 properties 文件读取模型连接参数。
 */
public final class SiliconFlowConfig {

    /**
     * SiliconFlow 默认 OpenAI 兼容接口地址。
     */
    public static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn/v1";

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    /**
     * 创建 SiliconFlow 配置。
     */
    public SiliconFlowConfig(String apiKey, String baseUrl, String model) {
        this.apiKey = trimToEmpty(apiKey);
        this.baseUrl = trimToDefault(baseUrl, DEFAULT_BASE_URL);
        this.model = trimToEmpty(model);
    }

    /**
     * 从指定 properties 文件加载配置。
     */
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

    /**
     * 从本地文件或 classpath 默认文件加载配置。
     */
    public static SiliconFlowConfig loadDefault() {
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

    /**
     * 返回 API Key。
     */
    public String apiKey() {
        return apiKey;
    }

    /**
     * 返回 OpenAI 兼容接口 base URL。
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 返回模型名称。
     */
    public String model() {
        return model;
    }

    /**
     * JavaBean 风格 API Key getter。
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * JavaBean 风格 base URL getter。
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * JavaBean 风格模型名称 getter。
     */
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
