package com.jaising.agent.state;

import com.jaising.agent.domain.AgentState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryStateStore implements StateStore {

  private final Map<String, AgentState> states = new HashMap<String, AgentState>();

  @Override
  public Optional<AgentState> load(String taskId) {
    return Optional.ofNullable(states.get(taskId));
  }

  @Override
  public void save(AgentState state) {
    states.put(state.taskId(), state);
  }
}
