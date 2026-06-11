package io.github.tinyclaw.agent.runtime;

import java.util.Objects;

/**
 * 单次工具调用的可观测指标。
 */
public final class ToolCallMetric {

    private final String toolName;
    private final long durationMillis;
    private final boolean success;
    private final int outputBytes;
    private final String errorMessage;

    public ToolCallMetric(String toolName, long durationMillis, boolean success, int outputBytes,
            String errorMessage) {
        this.toolName = toolName == null ? "" : toolName;
        this.durationMillis = Math.max(0L, durationMillis);
        this.success = success;
        this.outputBytes = Math.max(0, outputBytes);
        this.errorMessage = errorMessage;
    }

    public String toolName() {
        return toolName;
    }

    public String getToolName() {
        return toolName();
    }

    public long durationMillis() {
        return durationMillis;
    }

    public long getDurationMillis() {
        return durationMillis();
    }

    public boolean success() {
        return success;
    }

    public boolean isSuccess() {
        return success();
    }

    public int outputBytes() {
        return outputBytes;
    }

    public int getOutputBytes() {
        return outputBytes();
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolCallMetric)) {
            return false;
        }
        ToolCallMetric that = (ToolCallMetric) other;
        return durationMillis == that.durationMillis
                && success == that.success
                && outputBytes == that.outputBytes
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, durationMillis, success, outputBytes, errorMessage);
    }
}
