package com.jaising.agent.middleware;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 白名单中间件
 * 只允许明确放行的工具
 */
public final class AllowListMiddleware implements ToolMiddleware {

  private final Set<String> allowedTools;

  /**
   * 创建白名单
   * 复制后转成不可变集合
   */
  public AllowListMiddleware(Set<String> allowedTools) {
    this.allowedTools = Collections.unmodifiableSet(new HashSet<String>(allowedTools));
  }

  /**
   * 执行前检查
   * 不在白名单就拒绝
   */
  @Override
  public MiddlewareDecision beforeTool(AgentState state, ToolCall call) {
    if (allowedTools.contains(call.toolName())) {
      return MiddlewareDecision.allow();
    }
    return MiddlewareDecision.deny("Tool not allowed: " + call.toolName());
  }
}
