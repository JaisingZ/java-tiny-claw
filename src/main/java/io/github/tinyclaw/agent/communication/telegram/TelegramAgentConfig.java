package io.github.tinyclaw.agent.communication.telegram;

import io.github.tinyclaw.agent.runtime.WorkingMemoryPolicy;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Telegram Webhook 宿主的应用配置。
 */
public final class TelegramAgentConfig {

    private static final boolean DEFAULT_ENABLE_THINKING = false;
    private static final boolean DEFAULT_PLAN_MODE = false;
    private static final boolean DEFAULT_DEBUG = false;
    private static final boolean DEFAULT_INTENT_FILTER_ENABLED = false;

    private final Path workDir;
    private final boolean enableThinking;
    private final boolean planMode;
    private final boolean debug;
    private final boolean intentFilterEnabled;
    private final List<String> intentFilterMarkers;
    private final WorkingMemoryPolicy workingMemoryPolicy;
    private final ToolPermissionConfig toolPermissionConfig;

    private TelegramAgentConfig(Path workDir, boolean enableThinking, boolean planMode, boolean debug,
            boolean intentFilterEnabled, List<String> intentFilterMarkers, WorkingMemoryPolicy workingMemoryPolicy,
            ToolPermissionConfig toolPermissionConfig) {
        this.workDir = workDir;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
        this.debug = debug;
        this.intentFilterEnabled = intentFilterEnabled;
        this.intentFilterMarkers = Collections.unmodifiableList(new ArrayList<String>(intentFilterMarkers));
        this.workingMemoryPolicy = workingMemoryPolicy;
        this.toolPermissionConfig = toolPermissionConfig;
    }

    public static TelegramAgentConfig from(Map<String, String> values) {
        boolean intentFilterEnabled = parseBoolean(optional(values, "agent.intentFilter.enabled",
                String.valueOf(DEFAULT_INTENT_FILTER_ENABLED)), "agent.intentFilter.enabled");
        List<String> intentFilterMarkers = parseIntentFilterMarkers(values);
        if (intentFilterEnabled && intentFilterMarkers.isEmpty()) {
            throw new IllegalStateException("agent.intentFilter.marker.* must be configured when intent filter is enabled");
        }
        return new TelegramAgentConfig(
                Path.of(optional(values, "agent.workdir", ".")),
                parseBoolean(optional(values, "agent.enableThinking", String.valueOf(DEFAULT_ENABLE_THINKING)),
                        "agent.enableThinking"),
                parseBoolean(optional(values, "agent.planMode", String.valueOf(DEFAULT_PLAN_MODE)),
                        "agent.planMode"),
                parseBoolean(optional(values, "agent.debug", String.valueOf(DEFAULT_DEBUG)),
                        "agent.debug"),
                intentFilterEnabled,
                intentFilterMarkers,
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
                return new TelegramAgentConfig(Path.of("."), DEFAULT_ENABLE_THINKING,
                        DEFAULT_PLAN_MODE,
                        DEFAULT_DEBUG,
                        DEFAULT_INTENT_FILTER_ENABLED,
                        Collections.emptyList(),
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

    public boolean enableThinking() {
        return enableThinking;
    }

    public boolean planMode() {
        return planMode;
    }

    public boolean debug() {
        return debug;
    }

    public boolean intentFilterEnabled() {
        return intentFilterEnabled;
    }

    public List<String> intentFilterMarkers() {
        return intentFilterMarkers;
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

    private static List<String> parseIntentFilterMarkers(Map<String, String> values) {
        TreeMap<Integer, String> markers = new TreeMap<Integer, String>();
        for (String key : values.keySet()) {
            if (!key.startsWith("agent.intentFilter.marker.")) {
                continue;
            }
            String suffix = key.substring("agent.intentFilter.marker.".length());
            try {
                int index = Integer.parseInt(suffix);
                if (index <= 0) {
                    throw new IllegalStateException("agent.intentFilter.marker index must be positive: " + key);
                }
                String marker = values.get(key);
                if (!hasText(marker)) {
                    throw new IllegalStateException(key + " must not be blank");
                }
                markers.put(index, marker.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid intent filter marker key: " + key, ex);
            }
        }
        return List.copyOf(markers.values());
    }
}
