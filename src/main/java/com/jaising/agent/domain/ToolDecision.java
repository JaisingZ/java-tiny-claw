package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 工具决策
 * 告诉运行时下一步执行哪个工具
 */
public final class ToolDecision implements Decision {

  private final ToolCall call;

  /**
   * 创建工具决策
   * 直接包住一次调用
   */
  public ToolDecision(ToolCall call) {
    this.call = call;
  }

  public ToolCall call() {
    return call;
  }

  public ToolCall getCall() {
    return call;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ToolDecision)) {
      return false;
    }
    ToolDecision that = (ToolDecision) other;
    return Objects.equals(call, that.call);
  }

  @Override
  public int hashCode() {
    return Objects.hash(call);
  }

  @Override
  public String toString() {
    return "ToolDecision{"
        + "call=" + call
        + '}';
  }
}
