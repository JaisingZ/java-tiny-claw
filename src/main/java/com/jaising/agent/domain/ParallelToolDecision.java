package com.jaising.agent.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 并行工具决策
 * 承载一组需要执行的工具调用
 */
public final class ParallelToolDecision implements Decision {
    private final List<ToolCall> calls;

    public ParallelToolDecision(List<ToolCall> calls) {
        if (calls == null) {
            this.calls = Collections.emptyList();
        } else {
            this.calls = Collections.unmodifiableList(calls);
        }
    }

    public List<ToolCall> getCalls() {
        return calls;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(calls);
    }

    @Override
    public String toString() {
        return "ParallelToolDecision{"
                + "calls=" + calls
                + '}';
    }
}
