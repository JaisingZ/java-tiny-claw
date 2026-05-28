package com.jaising.agent.middleware;

import java.util.Objects;

/**
 * 中间件决策
 * 标记是否允许继续
 */
public final class MiddlewareDecision {

    private final boolean allowed;
    private final String reason;

    /**
     * 创建决策
     * 仅供静态工厂使用
     */
    private MiddlewareDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    /**
     * 允许执行
     */
    public static MiddlewareDecision allow() {
        return new MiddlewareDecision(true, null);
    }

    /**
     * 拒绝执行
     */
    public static MiddlewareDecision deny(String reason) {
        return new MiddlewareDecision(false, reason);
    }

    /**
     * 执行 allowed 操作。
     */
    public boolean allowed() {
        return allowed;
    }

    /**
     * 执行 reason 操作。
     */
    public String reason() {
        return reason;
    }

    /**
     * 判断 Allowed。
     */
    public boolean isAllowed() {
        return allowed;
    }

    /**
     * 读取 Reason。
     */
    public String getReason() {
        return reason;
    }

    /**
     * 比较对象是否相等。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MiddlewareDecision)) {
            return false;
        }
        MiddlewareDecision that = (MiddlewareDecision) other;
        return allowed == that.allowed && Objects.equals(reason, that.reason);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason);
    }
}
