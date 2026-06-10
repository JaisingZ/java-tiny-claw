package io.github.tinyclaw.agent.tool.permission;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 工具权限配置。
 */
public final class ToolPermissionConfig {

    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_APPROVAL_TIMEOUT_SECONDS = 1_800;
    public static final String DEFAULT_PERMISSION_FILE = ".claw/permissions.yaml";
    public static final boolean DEFAULT_HOT_RELOAD = true;
    public static final int DEFAULT_RELOAD_INTERVAL_SECONDS = 2;

    private final boolean enabled;
    private final Duration approvalTimeout;
    private final Map<String, ToolPermissionAction> toolActions;
    private final List<Pattern> denyPatterns;
    private final Path permissionFile;
    private final boolean hotReload;
    private final Duration reloadInterval;
    private final boolean legacyConfigured;

    private ToolPermissionConfig(boolean enabled, Duration approvalTimeout,
            Map<String, ToolPermissionAction> toolActions, List<Pattern> denyPatterns,
            Path permissionFile, boolean hotReload, Duration reloadInterval, boolean legacyConfigured) {
        this.enabled = enabled;
        this.approvalTimeout = approvalTimeout;
        this.toolActions = Collections.unmodifiableMap(new LinkedHashMap<String, ToolPermissionAction>(toolActions));
        this.denyPatterns = Collections.unmodifiableList(new ArrayList<Pattern>(denyPatterns));
        this.permissionFile = permissionFile;
        this.hotReload = hotReload;
        this.reloadInterval = reloadInterval;
        this.legacyConfigured = legacyConfigured;
    }

    public static ToolPermissionConfig from(Map<String, String> values) {
        Map<String, String> source = values == null ? Collections.<String, String>emptyMap() : values;
        boolean enabled = parseBoolean(optional(source, "agent.permissions.enabled",
                String.valueOf(DEFAULT_ENABLED)), "agent.permissions.enabled");
        Duration timeout = Duration.ofSeconds(parsePositiveInt(optional(source,
                "agent.permissions.approvalTimeoutSeconds",
                String.valueOf(DEFAULT_APPROVAL_TIMEOUT_SECONDS)),
                "agent.permissions.approvalTimeoutSeconds"));
        Map<String, ToolPermissionAction> actions = defaultToolActions();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("agent.permissions.tool.")) {
                String toolName = key.substring("agent.permissions.tool.".length()).trim();
                if (toolName.isEmpty()) {
                    throw new IllegalStateException("Invalid permission tool key: " + key);
                }
                actions.put(toolName, ToolPermissionAction.parse(entry.getValue(), key));
            }
        }
        Path permissionFile = Path.of(optional(source, "agent.permissions.file", DEFAULT_PERMISSION_FILE));
        boolean hotReload = parseBoolean(optional(source, "agent.permissions.hotReload",
                String.valueOf(DEFAULT_HOT_RELOAD)), "agent.permissions.hotReload");
        Duration reloadInterval = Duration.ofSeconds(parsePositiveInt(optional(source,
                "agent.permissions.reloadIntervalSeconds",
                String.valueOf(DEFAULT_RELOAD_INTERVAL_SECONDS)),
                "agent.permissions.reloadIntervalSeconds"));
        return new ToolPermissionConfig(enabled, timeout, actions, parseDenyPatterns(source),
                permissionFile, hotReload, reloadInterval, legacyConfigured(source));
    }

    public boolean enabled() {
        return enabled;
    }

    public Duration approvalTimeout() {
        return approvalTimeout;
    }

    public Map<String, ToolPermissionAction> toolActions() {
        return toolActions;
    }

    public List<Pattern> denyPatterns() {
        return denyPatterns;
    }

    public Path permissionFile() {
        return permissionFile;
    }

    public boolean hotReload() {
        return hotReload;
    }

    public Duration reloadInterval() {
        return reloadInterval;
    }

    public boolean legacyConfigured() {
        return legacyConfigured;
    }

    private static boolean legacyConfigured(Map<String, String> values) {
        for (String key : values.keySet()) {
            if ("agent.permissions.enabled".equals(key)
                    || "agent.permissions.approvalTimeoutSeconds".equals(key)
                    || key.startsWith("agent.permissions.tool.")
                    || key.startsWith("agent.permissions.denyPattern.")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, ToolPermissionAction> defaultToolActions() {
        Map<String, ToolPermissionAction> actions = new LinkedHashMap<String, ToolPermissionAction>();
        actions.put("read_file", ToolPermissionAction.ALLOW);
        actions.put("write_file", ToolPermissionAction.ASK);
        actions.put("edit_file", ToolPermissionAction.ASK);
        actions.put("bash", ToolPermissionAction.ASK);
        return actions;
    }

    private static List<Pattern> parseDenyPatterns(Map<String, String> values) {
        List<String> keys = new ArrayList<String>();
        for (String key : values.keySet()) {
            if (key.startsWith("agent.permissions.denyPattern.")) {
                keys.add(key);
            }
        }
        Collections.sort(keys, Comparator.comparingInt(ToolPermissionConfig::patternIndex));
        List<Pattern> patterns = new ArrayList<Pattern>();
        for (String key : keys) {
            String value = values.get(key);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(value.trim()));
            } catch (PatternSyntaxException ex) {
                throw new IllegalStateException("Invalid regex for " + key + ": " + value, ex);
            }
        }
        return patterns;
    }

    private static int patternIndex(String key) {
        String suffix = key.substring("agent.permissions.denyPattern.".length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String optional(Map<String, String> values, String key, String defaultValue) {
        String value = values.get(key);
        if (value != null && !value.trim().isEmpty()) {
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
}
