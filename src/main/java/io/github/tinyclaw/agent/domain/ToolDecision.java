package io.github.tinyclaw.agent.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * main loop 的工具决策。
 * 表示本轮应执行一组工具调用。
 */
public final class ToolDecision implements Decision {

    private final List<ToolCall> calls;

    /**
     * 创建单工具决策。
     */
    public ToolDecision(ToolCall call) {
        this.calls = Collections.singletonList(call);
    }

    /**
     * 创建工具决策。
     * null 输入会转为空列表，返回的列表始终为不可变。
     */
    public ToolDecision(List<ToolCall> calls) {
        if (calls == null) {
            this.calls = Collections.emptyList();
        } else {
            this.calls = Collections.unmodifiableList(calls);
        }
    }

    /**
     * 获取唯一的待执行工具调用。
     */
    public ToolCall call() {
        if (calls.size() != 1) {
            throw new IllegalStateException("ToolDecision.call() requires exactly one tool call");
        }
        return calls.get(0);
    }

    /**
     * 获取唯一的待执行工具调用（兼容 get 风格调用）。
     */
    public ToolCall getCall() {
        return call();
    }

    /**
     * 获取待执行的工具调用列表。
     */
    public List<ToolCall> calls() {
        return calls;
    }

    /**
     * 获取待执行的工具调用列表（兼容 get 风格调用）。
     */
    public List<ToolCall> getCalls() {
        return calls();
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
        return Objects.equals(calls, that.calls);
    }

    /**
     * 计算工具决策哈希码。
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
        return "ToolDecision{"
                + "calls=" + calls
                + '}';
    }
}
