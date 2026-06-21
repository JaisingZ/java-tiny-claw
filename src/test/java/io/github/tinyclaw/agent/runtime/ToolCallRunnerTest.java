package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.observability.TraceScope;
import io.github.tinyclaw.agent.observability.TraceSpan;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCallRunnerTest {

    @Test
    void recordsToolTraceAndMetric() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);
        ToolRegistry registry = new ToolRegistry().register(new ReadOnlyTool());
        ToolCallRunner runner = new ToolCallRunner(registry, NoopRunLogger.INSTANCE, recorder);
        RunMetricsCollector metrics = new RunMetricsCollector();

        ToolResult result;
        try (TraceScope root = recorder.startRoot("agent.run")) {
            try (TraceScope turn = recorder.startChild(root.span(), "turn")) {
                result = runner.execute(
                        AgentContext.create(new Task("task-tool-runner", "read")),
                        new ToolCall("read_only", Collections.<String, Object>singletonMap("text", "hello")),
                        metrics,
                        turn.span());
            }
        }

        assertThat(result.output()).isEqualTo("hello");
        assertThat(metrics.snapshot().toolCalls()).hasSize(1);
        assertThat(metrics.snapshot().toolCalls().get(0).toolName()).isEqualTo("read_only");
        assertThat(metrics.snapshot().toolCalls().get(0).outputBytes()).isEqualTo(5);
        TraceSpan tool = exported.get(0).children().get(0).children().get(0);
        assertThat(tool.name()).isEqualTo("tool.execute");
        assertThat(tool.attributes())
                .containsEntry("tool_name", "read_only")
                .containsEntry("side_effect", false)
                .containsEntry("success", true)
                .containsEntry("output_bytes", 5);
        assertThat(String.valueOf(tool.attributes().get("arguments_preview"))).contains("hello");
    }

    private static final class ReadOnlyTool implements Tool {
        @Override
        public String name() {
            return "read_only";
        }

        @Override
        public boolean isSideEffect() {
            return false;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.success(String.valueOf(call.arguments().get("text")));
        }
    }
}
