package com.jaising.agent.domain;

import java.util.Objects;

public final class FinishDecision implements Decision {

  private final String answer;

  public FinishDecision(String answer) {
    this.answer = answer;
  }

  public String answer() {
    return answer;
  }

  public String getAnswer() {
    return answer;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof FinishDecision)) {
      return false;
    }
    FinishDecision that = (FinishDecision) other;
    return Objects.equals(answer, that.answer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(answer);
  }

  @Override
  public String toString() {
    return "FinishDecision{"
        + "answer='" + answer + '\''
        + '}';
  }
}
