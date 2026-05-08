package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

public interface ToolMiddleware {
  MiddlewareDecision beforeTool(AgentState state, ToolCall call);
}
