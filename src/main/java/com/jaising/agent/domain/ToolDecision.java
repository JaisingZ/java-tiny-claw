package com.jaising.agent.domain;

import java.util.Objects;

/**
 * main loop 的工具决策。
 * 表示本轮应执行单个工具调用。
 */
public final class ToolDecision implements Decision {

    private final ToolCall call;

    /**
     * 创建工具决策。
     */
    public ToolDecision(ToolCall call) {
        this.call = call;
    }

    /**
     * 获取待执行的工具调用。
     */
    public ToolCall call() {
        return call;
    }

    /**
     * 获取待执行的工具调用（兼容 get 风格调用）。
     */
    public ToolCall getCall() {
        return call;
    }

    /**
     * 判断工具决策是否等价。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolDecision)) {
            return false;
        }
        ToolDecision that = (ToolDecision) other;
        return Objects.equals(call, that.call);
    }

    /**
     * 计算工具决策哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(call);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "ToolDecision{"
                + "call=" + call
                + '}';
    }
}
