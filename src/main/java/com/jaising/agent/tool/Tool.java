package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

/**
 * 工具接口
 * 负责执行具体动作并返回结果
 */
public interface Tool {
  String name();

  ToolResult execute(ToolCall call, AgentState state);
}
