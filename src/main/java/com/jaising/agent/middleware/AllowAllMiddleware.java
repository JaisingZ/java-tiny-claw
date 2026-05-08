package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

public final class AllowAllMiddleware implements ToolMiddleware {
    @Override
    public MiddlewareDecision beforeTool(AgentState state, ToolCall call) {
        return MiddlewareDecision.allow();
    }
}
