package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class AllowListMiddleware implements ToolMiddleware {

  private final Set<String> allowedTools;

  public AllowListMiddleware(Set<String> allowedTools) {
    this.allowedTools = Collections.unmodifiableSet(new HashSet<String>(allowedTools));
  }

  @Override
  public MiddlewareDecision beforeTool(AgentState state, ToolCall call) {
    if (allowedTools.contains(call.toolName())) {
      return MiddlewareDecision.allow();
    }
    return MiddlewareDecision.deny("Tool not allowed: " + call.toolName());
  }
}
