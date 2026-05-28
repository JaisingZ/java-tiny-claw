package com.jaising.agent.runtime;

import com.jaising.agent.domain.AgentState;

import java.util.Objects;

/**
 * 运行结果
 * 封装最终状态便于调用方读取
 */
public final class RunResult {

    private final AgentState state;

    /**
     * 创建运行结果
     * 直接包住最终状态
     */
    public RunResult(AgentState state) {
        this.state = state;
    }

    /**
     * 执行 state 操作。
     */
    public AgentState state() {
        return state;
    }

    /**
     * 读取 State。
     */
    public AgentState getState() {
        return state;
    }

    /**
     * 按状态比较
     * 便于测试断言
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunResult that)) {
            return false;
        }
        return Objects.equals(state, that.state);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(state);
    }
}
