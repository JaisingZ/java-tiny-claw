package com.jaising.agent.provider;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;

public interface ModelProvider {
    Decision decide(AgentState state);
}
