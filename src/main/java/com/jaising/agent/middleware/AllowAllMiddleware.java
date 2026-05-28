package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

/**
 * 全放行中间件
 * 仅用于测试和默认场景
 */
public final class AllowAllMiddleware implements ToolMiddleware {
    /**
     * 执行工具前进行检查。
     */
    @Override
    public MiddlewareDecision beforeTool(AgentState state, ToolCall call) {
        return MiddlewareDecision.allow();
    }
}
