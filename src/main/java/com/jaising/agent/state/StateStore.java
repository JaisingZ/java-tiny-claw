package com.jaising.agent.state;

import com.jaising.agent.domain.AgentState;
import java.util.Optional;

/**
 * 状态存储
 * 负责恢复和保存任务状态
 */
public interface StateStore {
    /**
     * 读取任务状态。
     */
    Optional<AgentState> load(String taskId);

    /**
     * 保存任务状态。
     */
    void save(AgentState state);
}
