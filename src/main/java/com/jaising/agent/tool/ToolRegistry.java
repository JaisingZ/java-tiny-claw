package com.jaising.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册表
 * 负责命名 注册和查找
 */
public final class ToolRegistry {

  private final Map<String, Tool> tools = new LinkedHashMap<String, Tool>();

  /**
   * 注册工具
   */
  public ToolRegistry register(Tool tool) {
    tools.put(tool.name(), tool);
    return this;
  }

  /**
   * 按名称查找工具
   * 找不到就直接失败
   */
  public Tool require(String toolName) {
    Tool tool = tools.get(toolName);
    if (tool == null) {
      throw new IllegalArgumentException("Unknown tool: " + toolName);
    }
    return tool;
  }

  /**
   * 输出快照
   */
  public Map<String, Tool> snapshot() {
    return Collections.unmodifiableMap(tools);
  }
}
