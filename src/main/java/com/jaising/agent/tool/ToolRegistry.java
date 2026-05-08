package com.jaising.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolRegistry {

  private final Map<String, Tool> tools = new LinkedHashMap<String, Tool>();

  public ToolRegistry register(Tool tool) {
    tools.put(tool.name(), tool);
    return this;
  }

  public Tool require(String toolName) {
    Tool tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Unknown tool: " + toolName);
    }
    return tool;
  }

  public Map<String, Tool> snapshot() {
    return Collections.unmodifiableMap(tools);
  }
}
