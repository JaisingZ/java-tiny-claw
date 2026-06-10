package io.github.tinyclaw.agent.runtime;

import java.util.Objects;

/**
 * 一个 AgentSession 的累计可观测指标。
 */
public final class SessionMetrics {

    private static final SessionMetrics EMPTY = new SessionMetrics(0, 0, 0, 0, 0, 0, 0, 0L, 0, 0L, 0);

    private final int modelCallCount;
    private final int successfulModelCallCount;
    private final int failedModelCallCount;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final int usageUnavailableCount;
    private final long modelDurationMillis;
    private final int toolCallCount;
    private final long toolDurationMillis;
    private final int toolOutputBytes;

    public SessionMetrics(int modelCallCount, int successfulModelCallCount, int failedModelCallCount,
            int promptTokens, int completionTokens, int totalTokens, int usageUnavailableCount,
            long modelDurationMillis,
            int toolCallCount, long toolDurationMillis, int toolOutputBytes) {
        this.modelCallCount = Math.max(0, modelCallCount);
        this.successfulModelCallCount = Math.max(0, successfulModelCallCount);
        this.failedModelCallCount = Math.max(0, failedModelCallCount);
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = Math.max(0, totalTokens);
        this.usageUnavailableCount = Math.max(0, usageUnavailableCount);
        this.modelDurationMillis = Math.max(0L, modelDurationMillis);
        this.toolCallCount = Math.max(0, toolCallCount);
        this.toolDurationMillis = Math.max(0L, toolDurationMillis);
        this.toolOutputBytes = Math.max(0, toolOutputBytes);
    }

    public static SessionMetrics empty() {
        return EMPTY;
    }

    public SessionMetrics plus(RunMetrics metrics) {
        if (metrics == null) {
            return this;
        }
        return new SessionMetrics(
                modelCallCount + metrics.modelCallCount(),
                successfulModelCallCount + metrics.successfulModelCallCount(),
                failedModelCallCount + metrics.failedModelCallCount(),
                promptTokens + metrics.promptTokens(),
                completionTokens + metrics.completionTokens(),
                totalTokens + metrics.totalTokens(),
                usageUnavailableCount + metrics.usageUnavailableCount(),
                modelDurationMillis + metrics.modelDurationMillis(),
                toolCallCount + metrics.toolCallCount(),
                toolDurationMillis + metrics.toolDurationMillis(),
                toolOutputBytes + metrics.toolOutputBytes());
    }

    public int modelCallCount() {
        return modelCallCount;
    }

    public int successfulModelCallCount() {
        return successfulModelCallCount;
    }

    public int failedModelCallCount() {
        return failedModelCallCount;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    public int usageUnavailableCount() {
        return usageUnavailableCount;
    }

    public long modelDurationMillis() {
        return modelDurationMillis;
    }

    public int toolCallCount() {
        return toolCallCount;
    }

    public long toolDurationMillis() {
        return toolDurationMillis;
    }

    public int toolOutputBytes() {
        return toolOutputBytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionMetrics)) {
            return false;
        }
        SessionMetrics that = (SessionMetrics) other;
        return modelCallCount == that.modelCallCount
                && successfulModelCallCount == that.successfulModelCallCount
                && failedModelCallCount == that.failedModelCallCount
                && promptTokens == that.promptTokens
                && completionTokens == that.completionTokens
                && totalTokens == that.totalTokens
                && usageUnavailableCount == that.usageUnavailableCount
                && modelDurationMillis == that.modelDurationMillis
                && toolCallCount == that.toolCallCount
                && toolDurationMillis == that.toolDurationMillis
                && toolOutputBytes == that.toolOutputBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelCallCount, successfulModelCallCount, failedModelCallCount, promptTokens,
                completionTokens, totalTokens, usageUnavailableCount, modelDurationMillis, toolCallCount,
                toolDurationMillis, toolOutputBytes);
    }
}
