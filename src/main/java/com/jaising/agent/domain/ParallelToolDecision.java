package com.jaising.agent.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * main loop 的并行执行决策。
 * 一次包含多个工具调用，供运行时并发执行。
 */
public final class ParallelToolDecision implements Decision {
    private final List<ToolCall> calls;

    /**
     * 创建并行工具决策。
     * null 输入会转为空列表，返回的列表始终为不可变。
     */
    public ParallelToolDecision(List<ToolCall> calls) {
        if (calls == null) {
            this.calls = Collections.emptyList();
        } else {
            this.calls = Collections.unmodifiableList(calls);
        }
    }

    /**
     * 获取待执行的工具调用列表。
     */
    public List<ToolCall> getCalls() {
        return calls;
    }

    /**
     * 判断两份并行决策是否包含一致的调用列表。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParallelToolDecision)) {
            return false;
        }
        ParallelToolDecision that = (ParallelToolDecision) other;
        return Objects.equals(calls, that.calls);
    }

    /**
     * 计算并行决策哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(calls);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "ParallelToolDecision{"
                + "calls=" + calls
                + '}';
    }
}
