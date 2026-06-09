package io.github.tinyclaw.agent.tool.permission;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 工具权限判定策略。
 */
public final class ToolPermissionPolicy {

    private final ToolPermissionConfig config;

    public ToolPermissionPolicy(ToolPermissionConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ToolPermissionDecision evaluate(ToolCall call) {
        if (!config.enabled()) {
            return ToolPermissionDecision.allow("permissions disabled");
        }
        String candidate = call.toolName() + " " + call.arguments();
        for (Pattern pattern : config.denyPatterns()) {
            if (pattern.matcher(candidate).find()) {
                return ToolPermissionDecision.deny("Denied by pattern: " + pattern.pattern());
            }
        }
        ToolPermissionAction action = config.toolActions().get(call.toolName());
        if (action == null) {
            action = ToolPermissionAction.ASK;
        }
        if (action == ToolPermissionAction.ALLOW) {
            return ToolPermissionDecision.allow("Tool allowed: " + call.toolName());
        }
        if (action == ToolPermissionAction.DENY) {
            return ToolPermissionDecision.deny("Tool denied: " + call.toolName());
        }
        return ToolPermissionDecision.ask("Tool requires approval: " + call.toolName());
    }

    public static ToolPermissionPolicy from(Map<String, String> values) {
        return new ToolPermissionPolicy(ToolPermissionConfig.from(values));
    }
}
