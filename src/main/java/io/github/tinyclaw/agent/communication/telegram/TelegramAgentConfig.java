package io.github.tinyclaw.agent.communication.telegram;

import io.github.tinyclaw.agent.runtime.WorkingMemoryPolicy;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Telegram Webhook 宿主的应用配置。
 */
public final class TelegramAgentConfig {

    private static final int DEFAULT_MAX_STEPS = 8;
    private static final boolean DEFAULT_ENABLE_THINKING = false;
    private static final boolean DEFAULT_PLAN_MODE = false;

    private final Path workDir;
    private final int maxSteps;
    private final boolean enableThinking;
    private final boolean planMode;
    private final WorkingMemoryPolicy workingMemoryPolicy;
    private final ToolPermissionConfig toolPermissionConfig;

    private TelegramAgentConfig(Path workDir, int maxSteps, boolean enableThinking, boolean planMode,
            WorkingMemoryPolicy workingMemoryPolicy, ToolPermissionConfig toolPermissionConfig) {
        this.workDir = workDir;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
        this.workingMemoryPolicy = workingMemoryPolicy;
        this.toolPermissionConfig = toolPermissionConfig;
    }

    public static TelegramAgentConfig from(Map<String, String> values) {
        return new TelegramAgentConfig(
                Path.of(optional(values, "agent.workdir", ".")),
                parsePositiveInt(optional(values, "agent.maxSteps", String.valueOf(DEFAULT_MAX_STEPS)),
                        "agent.maxSteps"),
                parseBoolean(optional(values, "agent.enableThinking", String.valueOf(DEFAULT_ENABLE_THINKING)),
                        "agent.enableThinking"),
                parseBoolean(optional(values, "agent.planMode", String.valueOf(DEFAULT_PLAN_MODE)),
                        "agent.planMode"),
                new WorkingMemoryPolicy(
                        parsePositiveInt(optional(values, "agent.workingMemory.maxMessages",
                                String.valueOf(WorkingMemoryPolicy.DEFAULT_MAX_MESSAGES)),
                                "agent.workingMemory.maxMessages"),
                        parsePositiveInt(optional(values, "agent.workingMemory.maxChars",
                                String.valueOf(WorkingMemoryPolicy.DEFAULT_MAX_CHARS)),
                                "agent.workingMemory.maxChars")),
                ToolPermissionConfig.from(values));
    }

    static TelegramAgentConfig load(Path path) {
        return from(loadProperties(path));
    }

    static TelegramAgentConfig loadDefault() {
        Path localConfig = Path.of("agent.properties");
        if (Files.exists(localConfig)) {
            return load(localConfig);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = TelegramAgentConfig.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (inputStream == null) {
                return new TelegramAgentConfig(Path.of("."), DEFAULT_MAX_STEPS, DEFAULT_ENABLE_THINKING,
                        DEFAULT_PLAN_MODE,
                        new WorkingMemoryPolicy(),
                        ToolPermissionConfig.from(new HashMap<String, String>()));
            }
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath agent.properties", ex);
        }
        return from(toMap(properties));
    }

    public Path workDir() {
        return workDir;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public boolean enableThinking() {
        return enableThinking;
    }

    public boolean planMode() {
        return planMode;
    }

    public WorkingMemoryPolicy workingMemoryPolicy() {
        return workingMemoryPolicy;
    }

    public ToolPermissionConfig toolPermissionConfig() {
        return toolPermissionConfig;
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

    private static String optional(Map<String, String> values, String key, String defaultValue) {
        String value = values.get(key);
        if (hasText(value)) {
            return value.trim();
        }
        return defaultValue;
    }

    private static int parsePositiveInt(String value, String key) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalStateException(key + " must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid value for " + key + ": " + value, ex);
        }
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
