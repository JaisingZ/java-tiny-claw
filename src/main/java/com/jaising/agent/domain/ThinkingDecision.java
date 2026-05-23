package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 内部思考决策
 * 只承载计划文本 不代表可执行动作
 */
public final class ThinkingDecision implements Decision {

  private final String thought;

  /**
   * 创建内部思考
   */
  public ThinkingDecision(String thought) {
    this.thought = thought;
  }

  public String thought() {
    return thought;
  }

  public String getThought() {
    return thought;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ThinkingDecision)) {
      return false;
    }
    ThinkingDecision that = (ThinkingDecision) other;
    return Objects.equals(thought, that.thought);
  }

  @Override
  public int hashCode() {
    return Objects.hash(thought);
  }

  @Override
  public String toString() {
    return "ThinkingDecision{"
        + "thought='" + thought + '\''
        + '}';
  }
}
