package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

/**
 * 工具中间件
 * 在工具执行前统一拦截
 */
public interface ToolMiddleware {
  MiddlewareDecision beforeTool(AgentState state, ToolCall call);
}
