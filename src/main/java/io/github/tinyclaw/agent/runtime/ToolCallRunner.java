package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.observability.TraceScope;
import io.github.tinyclaw.agent.observability.TraceSpan;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.charset.StandardCharsets;

/**
 * 封装一次工具调用的日志、trace 和指标记录。
 */
final class ToolCallRunner {

    private final ToolRegistry toolRegistry;
    private final RunLogger runLogger;
    private final TraceRecorder traceRecorder;

    ToolCallRunner(ToolRegistry toolRegistry, RunLogger runLogger, TraceRecorder traceRecorder) {
        this.toolRegistry = toolRegistry;
        this.runLogger = runLogger;
        this.traceRecorder = traceRecorder;
    }

    ToolResult execute(AgentContext context, ToolCall call, RunMetricsCollector metrics, TraceSpan turnSpan) {
        long toolStart = System.nanoTime();
        try (TraceScope toolScope = traceRecorder.startChild(turnSpan, "tool.execute")) {
            TraceSpan toolSpan = toolScope.span();
            Tool tool = toolRegistry.snapshot().get(call.toolName());
            toolSpan.putAttribute("tool_name", call.toolName());
            toolSpan.putAttribute("side_effect", tool == null || tool.isSideEffect());
            toolSpan.putAttribute("arguments_preview", preview(String.valueOf(call.arguments()), 400));
            runLogger.toolStarted(call);
            ToolResult toolResult = toolRegistry.execute(call, context);
            long durationMillis = elapsedMillis(toolStart);
            int outputBytes = outputBytes(toolResult);
            runLogger.toolCompleted(call, toolResult, durationMillis);
            toolSpan.putAttribute("success", toolResult.success());
            toolSpan.putAttribute("output_bytes", outputBytes);
            if (!toolResult.success()) {
                toolSpan.putAttribute("error", toolResult.errorMessage());
            }
            metrics.recordToolCall(new ToolCallMetric(call.toolName(), durationMillis, toolResult.success(),
                    outputBytes, toolResult.errorMessage()));
            return toolResult;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static int outputBytes(ToolResult result) {
        if (result == null || !result.success() || result.output() == null) {
            return 0;
        }
        return result.output().getBytes(StandardCharsets.UTF_8).length;
    }

    private static String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }
}
