package io.github.tinyclaw.agent.tool.permission;

import java.util.Objects;

/**
 * 工具权限判定结果。
 */
public final class ToolPermissionDecision {

    private final ToolPermissionAction action;
    private final String reason;

    public ToolPermissionDecision(ToolPermissionAction action, String reason) {
        this.action = Objects.requireNonNull(action, "action");
        this.reason = reason == null ? "" : reason;
    }

    public static ToolPermissionDecision allow(String reason) {
        return new ToolPermissionDecision(ToolPermissionAction.ALLOW, reason);
    }

    public static ToolPermissionDecision ask(String reason) {
        return new ToolPermissionDecision(ToolPermissionAction.ASK, reason);
    }

    public static ToolPermissionDecision deny(String reason) {
        return new ToolPermissionDecision(ToolPermissionAction.DENY, reason);
    }

    public ToolPermissionAction action() {
        return action;
    }

    public String reason() {
        return reason;
    }

    public boolean isAllow() {
        return action == ToolPermissionAction.ALLOW;
    }

    public boolean isAsk() {
        return action == ToolPermissionAction.ASK;
    }

    public boolean isDeny() {
        return action == ToolPermissionAction.DENY;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolPermissionDecision)) {
            return false;
        }
        ToolPermissionDecision that = (ToolPermissionDecision) other;
        return action == that.action && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, reason);
    }
}
