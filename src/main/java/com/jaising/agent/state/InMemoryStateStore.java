package com.jaising.agent.state;

import com.jaising.agent.domain.AgentState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 内存状态存储
 * 仅用于测试和本地场景
 */
public final class InMemoryStateStore implements StateStore {

    private final Map<String, AgentState> states = new HashMap<String, AgentState>();

    /**
     * 读取状态
     */
    @Override
    public Optional<AgentState> load(String taskId) {
        return Optional.ofNullable(states.get(taskId));
    }

    /**
     * 保存状态
     */
    @Override
    public void save(AgentState state) {
        states.put(state.taskId(), state);
    }
}
