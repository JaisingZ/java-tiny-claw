package com.jaising.agent.middleware;

import java.util.Objects;

public final class MiddlewareDecision {

  private final boolean allowed;
  private final String reason;

  private MiddlewareDecision(boolean allowed, String reason) {
    this.allowed = allowed;
    this.reason = reason;
  }

  public static MiddlewareDecision allow() {
    return new MiddlewareDecision(true, null);
  }

  public static MiddlewareDecision deny(String reason) {
    return new MiddlewareDecision(false, reason);
  }

  public boolean allowed() {
    return allowed;
  }

  public String reason() {
    return reason;
  }

  public boolean isAllowed() {
    return allowed;
  }

  public String getReason() {
    return reason;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MiddlewareDecision)) {
      return false;
    }
    MiddlewareDecision that = (MiddlewareDecision) other;
    return allowed == that.allowed && Objects.equals(reason, that.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(allowed, reason);
  }
}
