package io.github.tinyclaw.agent.tool.permission;

import java.util.Locale;

/**
 * 工具权限动作。
 */
public enum ToolPermissionAction {
    ALLOW,
    ASK,
    DENY;

    /**
     * 从配置文本解析权限动作。
     */
    public static ToolPermissionAction parse(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing permission action for " + key);
        }
        try {
            return ToolPermissionAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid permission action for " + key + ": " + value, ex);
        }
    }
}
