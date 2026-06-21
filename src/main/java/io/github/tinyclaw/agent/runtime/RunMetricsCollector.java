package io.github.tinyclaw.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 收集单次 run 的模型与工具调用指标。
 */
final class RunMetricsCollector {

    private final List<ModelCallMetric> modelCalls =
            Collections.synchronizedList(new ArrayList<ModelCallMetric>());
    private final List<ToolCallMetric> toolCalls =
            Collections.synchronizedList(new ArrayList<ToolCallMetric>());

    void recordModelCall(ModelCallMetric metric) {
        modelCalls.add(metric);
    }

    void recordToolCall(ToolCallMetric metric) {
        toolCalls.add(metric);
    }

    RunMetrics snapshot() {
        synchronized (modelCalls) {
            synchronized (toolCalls) {
                return new RunMetrics(modelCalls, toolCalls);
            }
        }
    }
}
