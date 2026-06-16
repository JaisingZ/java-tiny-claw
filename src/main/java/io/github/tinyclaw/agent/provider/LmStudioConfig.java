package io.github.tinyclaw.agent.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * LM Studio 连接配置。
 * 从 properties 文件读取模型连接参数。
 */
public final class LmStudioConfig {

    /**
     * LM Studio 默认 OpenAI 兼容接口地址。
     */
    public static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";
    public static final int DEFAULT_THINKING_MAX_TOKENS = 256;
    public static final int DEFAULT_ACTION_MAX_TOKENS = 1024;

    private final String baseUrl;
    private final String model;
    private final int thinkingMaxTokens;
    private final int actionMaxTokens;

    /**
     * 创建 LM Studio 配置。
     */
    public LmStudioConfig(String baseUrl, String model) {
        this(baseUrl, model, DEFAULT_THINKING_MAX_TOKENS, DEFAULT_ACTION_MAX_TOKENS);
    }

    public LmStudioConfig(String baseUrl, String model, int thinkingMaxTokens, int actionMaxTokens) {
        this.baseUrl = trimToDefault(baseUrl, DEFAULT_BASE_URL);
        this.model = trimToEmpty(model);
        this.thinkingMaxTokens = positiveOrDefault(thinkingMaxTokens, DEFAULT_THINKING_MAX_TOKENS);
        this.actionMaxTokens = positiveOrDefault(actionMaxTokens, DEFAULT_ACTION_MAX_TOKENS);
    }

    /**
     * 从指定 properties 文件加载配置。
     */
    public static LmStudioConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent config: " + path, ex);
        }
        return new LmStudioConfig(
                properties.getProperty("lmstudio.baseUrl"),
                properties.getProperty("lmstudio.model"),
                positiveInt(properties.getProperty("lmstudio.thinkingMaxTokens"), DEFAULT_THINKING_MAX_TOKENS),
                positiveInt(properties.getProperty("lmstudio.actionMaxTokens"), DEFAULT_ACTION_MAX_TOKENS));
    }

    /**
     * 从本地文件或 classpath 默认文件加载配置。
     */
    public static LmStudioConfig loadDefault() {
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
                properties.getProperty("lmstudio.model"),
                positiveInt(properties.getProperty("lmstudio.thinkingMaxTokens"), DEFAULT_THINKING_MAX_TOKENS),
                positiveInt(properties.getProperty("lmstudio.actionMaxTokens"), DEFAULT_ACTION_MAX_TOKENS));
    }

    /**
     * 返回 OpenAI 兼容接口 base URL。
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 返回 LM Studio 当前模型名称。
     */
    public String model() {
        return model;
    }

    public int thinkingMaxTokens() {
        return thinkingMaxTokens;
    }

    public int actionMaxTokens() {
        return actionMaxTokens;
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

    public int getThinkingMaxTokens() {
        return thinkingMaxTokens;
    }

    public int getActionMaxTokens() {
        return actionMaxTokens;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static int positiveInt(String value, int defaultValue) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed <= 0) {
                throw new IllegalArgumentException("max tokens must be positive: " + value);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid max tokens: " + value, ex);
        }
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
