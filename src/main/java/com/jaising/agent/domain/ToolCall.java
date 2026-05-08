package com.jaising.agent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ToolCall {

  private final String toolName;
  private final Map<String, Object> arguments;

  public ToolCall(String toolName, Map<String, Object> arguments) {
    this.toolName = toolName;
    this.arguments = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(arguments));
  }

  public String toolName() {
    return toolName;
  }

  public Map<String, Object> arguments() {
    return arguments;
  }

  public String getToolName() {
    return toolName;
  }

  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ToolCall)) {
      return false;
    }
    ToolCall that = (ToolCall) other;
    return Objects.equals(toolName, that.toolName) && Objects.equals(arguments, that.arguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(toolName, arguments);
  }

  @Override
  public String toString() {
    return "ToolCall{"
        + "toolName='" + toolName + '\''
        + ", arguments=" + arguments
        + '}';
  }
}
