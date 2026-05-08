package com.jaising.agent.state;

import com.jaising.agent.domain.AgentState;
import java.util.Optional;

public interface StateStore {
  Optional<AgentState> load(String taskId);

  void save(AgentState state);
}
