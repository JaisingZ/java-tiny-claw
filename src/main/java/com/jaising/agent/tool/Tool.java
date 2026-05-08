package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;

public interface Tool {
  String name();

  ToolResult execute(ToolCall call, AgentState state);
}
