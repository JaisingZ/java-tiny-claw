package com.jaising.agent.tool;

import java.util.Objects;

/**
 * 工具结果
 * 统一表示成功和失败
 */
public final class ToolResult {

  private final boolean success;
  private final String output;
  private final String errorMessage;

  /**
   * 创建工具结果
   * 仅通过静态方法构造
   */
  private ToolResult(boolean success, String output, String errorMessage) {
    this.success = success;
    this.output = output;
    this.errorMessage = errorMessage;
  }

  /**
   * 成功结果
   */
  public static ToolResult success(String output) {
    return new ToolResult(true, output, null);
  }

  /**
   * 失败结果
   */
  public static ToolResult failure(String errorMessage) {
    return new ToolResult(false, null, errorMessage);
  }

  public boolean success() {
    return success;
  }

  public String output() {
    return output;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getOutput() {
    return output;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ToolResult)) {
      return false;
    }
    ToolResult that = (ToolResult) other;
    return success == that.success
        && Objects.equals(output, that.output)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(success, output, errorMessage);
  }

  @Override
  public String toString() {
    return "ToolResult{"
        + "success=" + success
        + ", output='" + output + '\''
        + ", errorMessage='" + errorMessage + '\''
        + '}';
  }
}
