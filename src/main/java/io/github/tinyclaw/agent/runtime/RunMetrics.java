package io.github.tinyclaw.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单次 Agent run 的模型和工具调用指标。
 */
public final class RunMetrics {

    private static final RunMetrics EMPTY = new RunMetrics(
            Collections.<ModelCallMetric>emptyList(),
            Collections.<ToolCallMetric>emptyList());

    private final List<ModelCallMetric> modelCalls;
    private final List<ToolCallMetric> toolCalls;

    public RunMetrics(List<ModelCallMetric> modelCalls, List<ToolCallMetric> toolCalls) {
        this.modelCalls = Collections.unmodifiableList(new ArrayList<ModelCallMetric>(
                modelCalls == null ? Collections.<ModelCallMetric>emptyList() : modelCalls));
        this.toolCalls = Collections.unmodifiableList(new ArrayList<ToolCallMetric>(
                toolCalls == null ? Collections.<ToolCallMetric>emptyList() : toolCalls));
    }

    public static RunMetrics empty() {
        return EMPTY;
    }

    public List<ModelCallMetric> modelCalls() {
        return modelCalls;
    }

    public List<ModelCallMetric> getModelCalls() {
        return modelCalls();
    }

    public List<ToolCallMetric> toolCalls() {
        return toolCalls;
    }

    public List<ToolCallMetric> getToolCalls() {
        return toolCalls();
    }

    public int modelCallCount() {
        return modelCalls.size();
    }

    public int successfulModelCallCount() {
        int count = 0;
        for (ModelCallMetric metric : modelCalls) {
            if (metric.success()) {
                count++;
            }
        }
        return count;
    }

    public int failedModelCallCount() {
        return modelCallCount() - successfulModelCallCount();
    }

    public int promptTokens() {
        int total = 0;
        for (ModelCallMetric metric : modelCalls) {
            total += metric.usage().promptTokens();
        }
        return total;
    }

    public int completionTokens() {
        int total = 0;
        for (ModelCallMetric metric : modelCalls) {
            total += metric.usage().completionTokens();
        }
        return total;
    }

    public int totalTokens() {
        int total = 0;
        for (ModelCallMetric metric : modelCalls) {
            total += metric.usage().totalTokens();
        }
        return total;
    }

    public int usageUnavailableCount() {
        int count = 0;
        for (ModelCallMetric metric : modelCalls) {
            if (!metric.usageAvailable()) {
                count++;
            }
        }
        return count;
    }

    public long modelDurationMillis() {
        long total = 0L;
        for (ModelCallMetric metric : modelCalls) {
            total += metric.durationMillis();
        }
        return total;
    }

    public int toolCallCount() {
        return toolCalls.size();
    }

    public long toolDurationMillis() {
        long total = 0L;
        for (ToolCallMetric metric : toolCalls) {
            total += metric.durationMillis();
        }
        return total;
    }

    public int toolOutputBytes() {
        int total = 0;
        for (ToolCallMetric metric : toolCalls) {
            total += metric.outputBytes();
        }
        return total;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunMetrics)) {
            return false;
        }
        RunMetrics that = (RunMetrics) other;
        return Objects.equals(modelCalls, that.modelCalls)
                && Objects.equals(toolCalls, that.toolCalls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelCalls, toolCalls);
    }
}
