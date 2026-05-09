package com.jaising.agent.state;

import com.jaising.agent.domain.AgentState;
import java.util.Optional;

/**
 * 状态存储
 * 负责恢复和保存任务状态
 */
public interface StateStore {
  Optional<AgentState> load(String taskId);

  void save(AgentState state);
}
