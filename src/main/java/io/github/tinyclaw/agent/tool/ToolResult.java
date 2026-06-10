package io.github.tinyclaw.agent.tool;

import java.util.Objects;

/**
 * 工具执行结果。
 * 统一表示成功输出和失败原因。
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
     * 创建成功结果。
     */
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }

    /**
     * 创建失败结果。
     */
    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage);
    }

    /**
     * 返回工具是否成功。
     */
    public boolean success() {
        return success;
    }

    /**
     * 返回工具成功输出。
     */
    public String output() {
        return output;
    }

    /**
     * 返回工具失败原因。
     */
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * JavaBean 风格成功标记 getter。
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * JavaBean 风格输出 getter。
     */
    public String getOutput() {
        return output;
    }

    /**
     * JavaBean 风格失败原因 getter。
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 比较对象是否相等。
     */
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

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(success, output, errorMessage);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "ToolResult{"
                + "success=" + success
                + ", output='" + output + '\''
                + ", errorMessage='" + errorMessage + '\''
                + '}';
    }
}
