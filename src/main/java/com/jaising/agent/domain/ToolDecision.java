package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 工具决策
 * 告诉运行时下一步执行哪个工具
 */
public final class ToolDecision implements Decision {

    private final ToolCall call;

    /**
     * 创建工具决策
     * 直接包住一次调用
     */
    public ToolDecision(ToolCall call) {
        this.call = call;
    }

    /**
     * 执行 call 操作。
     */
    public ToolCall call() {
        return call;
    }

    /**
     * 读取 Call。
     */
    public ToolCall getCall() {
        return call;
    }

    /**
     * 比较对象是否相等。
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
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(call);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "ToolDecision{"
                + "call=" + call
                + '}';
    }
}
