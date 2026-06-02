package com.jaising.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ParallelToolDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * 主循环测试
 * 覆盖成功和关键失败路径
 */
class AgentEngineTest {

    /**
     * 工具执行后正常结束
     */
    @Test
    void runsToolThenFinishes() {
        EngineFixture fixture = fixture().withTools(new EchoTool());

        RunResult result = fixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-1", "echo once");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().finalAnswer()).isEqualTo("done");
        assertThat(result.state().observations()).containsExactly("hello");
        assertThat(fixture.store.load("task-1")).isPresent();
        assertThat(fixture.store.load("task-1").orElseThrow().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(fixture.trace.events()).extracting(TraceEvent::type).contains(
                TraceEventType.MODEL_REQUEST,
                TraceEventType.TOOL_CALL,
                TraceEventType.TOOL_RESULT,
                TraceEventType.MODEL_REQUEST,
                TraceEventType.FINISHED);
    }

    /**
     * 关闭慢思考时保持单阶段循环
     */
    @Test
    void doesNotEmitThinkingTraceWhenThinkingIsDisabled() {
        EngineFixture fixture = fixture().withTools(new EchoTool());

        RunResult result = fixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-1-disabled", "echo once");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(fixture.trace.events()).extracting(TraceEvent::type).doesNotContain(
                TraceEventType.THINKING_REQUEST,
                TraceEventType.THINKING_RESPONSE);
    }

    /**
     * 找不到工具时失败
     */
    @Test
    void failsWhenToolIsMissing() {
        EngineFixture fixture = fixture();

        RunResult result = fixture.run(constantProvider(tool("missing")), "task-2", "missing tool");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("Unknown tool: missing");
    }

    /**
     * 工具返回失败时主循环失败并保留工具结果 trace
     */
    @Test
    void failsWhenToolReturnsFailure() {
        EngineFixture fixture = fixture().withTools(new FailingTool());

        RunResult result = fixture.run(constantProvider(tool("fail_tool")), "task-tool-fails", "tool fails");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("read failed");
        assertThat(fixture.trace.events())
                .filteredOn(event -> event.type() == TraceEventType.TOOL_RESULT)
                .extracting(TraceEvent::detail)
                .containsExactly("success=false error=read failed");
    }

    /**
     * 达到步数上限时失败
     */
    @Test
    void failsWhenMaxStepsIsExceeded() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withMaxSteps(1);

        RunResult result = fixture.run(constantProvider(tool("echo", "text", "hello")), "task-4", "loop forever");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("max_steps_exceeded");
        assertThat(result.state().stepCount()).isEqualTo(1);
    }

    /**
     * 开启慢思考时 每轮先思考 再行动
     */
    @Test
    void runsThinkingBeforeActionWhenEnabled() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true);
        ThinkingScriptedProvider provider = new ThinkingScriptedProvider();

        RunResult result = fixture.run(provider, "task-5", "echo once");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().finalAnswer()).isEqualTo("done");
        assertThat(result.state().observations()).containsExactly("hello");
        assertThat(result.state().lastThought()).isEqualTo("plan to finish");
        assertThat(provider.phases()).containsExactly(
                DecisionPhase.THINKING,
                DecisionPhase.ACTION,
                DecisionPhase.THINKING,
                DecisionPhase.ACTION);
        assertThat(provider.toolsByPhase().get(0)).isEmpty();
        assertThat(provider.toolsByPhase().get(1)).containsExactly(new ToolDefinition(
                "echo",
                "echo",
                Collections.<String, Object>singletonMap("type", "object")));
        assertThat(fixture.trace.events()).extracting(TraceEvent::type).containsSubsequence(
                TraceEventType.THINKING_REQUEST,
                TraceEventType.THINKING_RESPONSE,
                TraceEventType.MODEL_REQUEST,
                TraceEventType.MODEL_RESPONSE,
                TraceEventType.TOOL_CALL,
                TraceEventType.TOOL_RESULT,
                TraceEventType.FINISHED);
        assertThat(fixture.trace.events())
                .filteredOn(event -> event.type() == TraceEventType.THINKING_RESPONSE)
                .extracting(TraceEvent::detail)
                .containsExactly("ThinkingDecision thought=plan to call echo",
                        "ThinkingDecision thought=plan to finish");
        assertThat(fixture.trace.events())
                .filteredOn(event -> event.type() == TraceEventType.THINKING_REQUEST)
                .extracting(TraceEvent::detail)
                .first()
                .asString()
                .contains("enableThinking=true", "phase=THINKING", "tools=[]");
        assertThat(fixture.trace.events())
                .filteredOn(event -> event.type() == TraceEventType.MODEL_REQUEST)
                .extracting(TraceEvent::detail)
                .first()
                .asString()
                .contains("phase=ACTION", "tools=[echo]", "observations=0");
        assertThat(fixture.trace.events())
                .filteredOn(event -> event.type() == TraceEventType.MODEL_RESPONSE)
                .extracting(TraceEvent::detail)
                .first()
                .asString()
                .contains("ToolDecision tool=echo", "args={text=hello}");
    }

    /**
     * 可读运行日志按主循环顺序输出关键节点
     */
    @Test
    void emitsReadableRunLoggerEventsInLoopOrder() {
        RecordingRunLogger runLogger = new RecordingRunLogger();
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true).withRunLogger(runLogger);

        RunResult result = fixture.run(new ThinkingScriptedProvider(), "task-readable-log", "echo once");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(runLogger.events()).containsSubsequence(
                "turn:1",
                "thinking-start",
                "thinking-complete:plan to call echo",
                "action-start:[echo]",
                "tool-decision:echo",
                "tool-start:echo",
                "tool-success:echo",
                "turn:2",
                "thinking-start",
                "thinking-complete:plan to finish",
                "action-start:[echo]",
                "finish:done");
    }

    /**
     * 慢思考阶段模型异常直接失败
     */
    @Test
    void failsWhenThinkingProviderThrows() {
        EngineFixture fixture = fixture().withThinking(true);

        RunResult result = fixture.run((state, phase, tools) -> {
            if (phase == DecisionPhase.THINKING) {
                throw new RuntimeException("boom");
            }
            return finish("unused");
        }, "task-6", "think fails");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("provider_error: boom");
    }

    /**
     * 慢思考阶段不允许直接调用工具
     */
    @Test
    void failsWhenThinkingReturnsToolDecision() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true);

        RunResult result = fixture.run(constantProvider(tool("echo", "text", "hello")), "task-7", "bad thinking");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("unsupported_thinking_decision");
        assertThat(result.state().observations()).isEmpty();
    }

    /**
     * 并行工具决策执行成功
     */
    @Test
    void runsParallelToolsInDeclaredOrder() {
        EngineFixture fixture = fixture()
                .withTools(new ReadOnlyEchoTool("read1", "hello"), new ReadOnlyEchoTool("read2", "world"));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("read2")),
                finish("done")), "task-parallel", "parallel echo");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().observations()).containsExactly("hello\n\nworld");
    }

    /**
     * 并行决策中的未知工具保持与单工具相同的失败文案
     */
    @Test
    void failsWhenParallelDecisionContainsMissingTool() {
        EngineFixture fixture = fixture().withTools(new ReadOnlyEchoTool("read1", "hello"));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("missing"))), "task-parallel-missing", "parallel missing");

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("Unknown tool: missing");
    }

    /**
     * 空并行决策也会推进一步并持久化
     */
    @Test
    void advancesAndPersistsWhenParallelDecisionIsEmpty() {
        EngineFixture fixture = fixture();

        RunResult result = fixture.run(scriptedProvider(
                parallel(),
                finish("done")), "task-parallel-empty", "parallel empty");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().stepCount()).isEqualTo(1);
        assertThat(result.state().observations()).isEmpty();
        assertThat(fixture.store.load("task-parallel-empty").orElseThrow().stepCount()).isEqualTo(1);
    }

    /**
     * 混合只读和副作用工具时 结果顺序仍以决策声明顺序为准
     */
    @Test
    void keepsDeclaredOutputOrderForMixedParallelTools() {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<String>());
        EngineFixture fixture = fixture().withTools(
                new TrackingTool("read1", "hello", false, executionOrder),
                new TrackingTool("write1", "write-a", true, executionOrder),
                new TrackingTool("read2", "world", false, executionOrder),
                new TrackingTool("write2", "write-b", true, executionOrder));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("write1"), call("read2"), call("write2")),
                finish("done")), "task-parallel-mixed", "parallel mixed");

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().observations()).containsExactly("hello\n\nwrite-a\n\nworld\n\nwrite-b");
        assertThat(executionOrder).containsSubsequence("write1", "write2");
    }

    /**
     * 单工具和并行工具都只推进一步并追加一条观测
     */
    @Test
    void keepsStepAndObservationSemanticsForSingleAndParallelTools() {
        EngineFixture singleFixture = fixture().withTools(new EchoTool());
        RunResult singleResult = singleFixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-single-shape", "single shape");

        EngineFixture parallelFixture = fixture()
                .withTools(new ReadOnlyEchoTool("read1", "hello"), new ReadOnlyEchoTool("read2", "world"));
        RunResult parallelResult = parallelFixture.run(scriptedProvider(
                parallel(call("read1"), call("read2")),
                finish("done")), "task-parallel-shape", "parallel shape");

        assertThat(singleResult.state().stepCount()).isEqualTo(1);
        assertThat(singleResult.state().observations()).hasSize(1);
        assertThat(parallelResult.state().stepCount()).isEqualTo(1);
        assertThat(parallelResult.state().observations()).hasSize(1);
    }

    private EngineFixture fixture() {
        return new EngineFixture();
    }

    private static ModelProvider scriptedProvider(final Decision... decisions) {
        return new ModelProvider() {
            private int index = 0;

            @Override
            public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
                int current = index;
                if (current < decisions.length - 1) {
                    index++;
                }
                return decisions[current];
            }
        };
    }

    private static ModelProvider constantProvider(final Decision decision) {
        return (state, phase, availableTools) -> decision;
    }

    private static ToolDecision tool(String toolName, Object... arguments) {
        return new ToolDecision(call(toolName, arguments));
    }

    private static FinishDecision finish(String answer) {
        return new FinishDecision(answer);
    }

    private static ParallelToolDecision parallel(ToolCall... calls) {
        return new ParallelToolDecision(Arrays.asList(calls));
    }

    private static ToolCall call(String toolName, Object... arguments) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < arguments.length; i += 2) {
            values.put(String.valueOf(arguments[i]), arguments[i + 1]);
        }
        return new ToolCall(toolName, values);
    }

    /**
     * 按阶段返回思考 行动和结束
     */
    private static final class ThinkingScriptedProvider implements ModelProvider {
        private final List<DecisionPhase> phases = new ArrayList<DecisionPhase>();
        private final List<List<ToolDefinition>> toolsByPhase = new ArrayList<List<ToolDefinition>>();

        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            phases.add(phase);
            toolsByPhase.add(availableTools);
            if (phase == DecisionPhase.THINKING) {
                if (state.observations().isEmpty()) {
                    return new ThinkingDecision("plan to call echo");
                }
                return new ThinkingDecision("plan to finish");
            }
            if (state.observations().isEmpty()) {
                return tool("echo", "text", "hello");
            }
            return finish("done");
        }

        List<DecisionPhase> phases() {
            return phases;
        }

        List<List<ToolDefinition>> toolsByPhase() {
            return toolsByPhase;
        }
    }

    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success(String.valueOf(call.arguments().get("text")));
        }
    }

    private static final class ReadOnlyEchoTool implements Tool {
        private final String name;
        private final String output;

        private ReadOnlyEchoTool(String name, String output) {
            this.name = name;
            this.output = output;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success(output);
        }

        @Override
        public boolean isSideEffect() {
            return false;
        }
    }

    private static final class TrackingTool implements Tool {
        private final String name;
        private final String output;
        private final boolean sideEffect;
        private final List<String> executionOrder;

        private TrackingTool(String name, String output, boolean sideEffect, List<String> executionOrder) {
            this.name = name;
            this.output = output;
            this.sideEffect = sideEffect;
            this.executionOrder = executionOrder;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            executionOrder.add(name);
            return ToolResult.success(output);
        }

        @Override
        public boolean isSideEffect() {
            return sideEffect;
        }
    }

    /**
     * 总是返回失败的工具
     */
    private static final class FailingTool implements Tool {
        @Override
        public String name() {
            return "fail_tool";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.failure("read failed");
        }
    }

    private static class RunLoggerAdapter implements RunLogger {
        @Override
        public void writeLine(String line) {
        }

        @Override
        public void writeBlankLine() {
        }

        @Override
        public void registryMounted(String toolName) {
        }

        @Override
        public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
                List<ToolDefinition> tools) {
        }

        @Override
        public void turnStarted(int turn) {
        }

        @Override
        public void thinkingStarted() {
        }

        @Override
        public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        }

        @Override
        public void actionStarted(List<ToolDefinition> tools) {
        }

        @Override
        public void toolDecision(ToolDecision decision) {
        }

        @Override
        public void toolStarted(ToolCall call) {
        }

        @Override
        public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        }

        @Override
        public void finished(FinishDecision decision) {
        }

        @Override
        public void failed(String reason) {
        }
    }

    /**
     * 记录运行日志事件
     */
    private static final class RecordingRunLogger extends RunLoggerAdapter {
        private final List<String> events = new ArrayList<String>();

        @Override
        public void turnStarted(int turn) {
            events.add("turn:" + turn);
        }

        @Override
        public void thinkingStarted() {
            events.add("thinking-start");
        }

        @Override
        public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
            events.add("thinking-complete:" + decision.thought());
        }

        @Override
        public void actionStarted(List<ToolDefinition> tools) {
            events.add("action-start:" + toolNames(tools));
        }

        @Override
        public void toolDecision(ToolDecision decision) {
            events.add("tool-decision:" + decision.call().toolName());
        }

        @Override
        public void toolStarted(ToolCall call) {
            events.add("tool-start:" + call.toolName());
        }

        @Override
        public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
            events.add((result.success() ? "tool-success:" : "tool-failed:") + call.toolName());
        }

        @Override
        public void finished(FinishDecision decision) {
            events.add("finish:" + decision.answer());
        }

        @Override
        public void failed(String reason) {
            events.add("failed:" + reason);
        }

        private List<String> events() {
            return events;
        }

        private List<String> toolNames(List<ToolDefinition> tools) {
            List<String> names = new ArrayList<String>();
            for (ToolDefinition tool : tools) {
                names.add(tool.name());
            }
            return names;
        }
    }

    private final class EngineFixture {
        private final InMemoryStateStore store = new InMemoryStateStore();
        private final InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        private final ToolRegistry registry = new ToolRegistry();
        private RunLogger runLogger = new RunLoggerAdapter();
        private int maxSteps = 4;
        private boolean thinking = false;

        private EngineFixture withTools(Tool... tools) {
            for (Tool tool : tools) {
                registry.register(tool);
            }
            return this;
        }

        private EngineFixture withMaxSteps(int value) {
            maxSteps = value;
            return this;
        }

        private EngineFixture withThinking(boolean value) {
            thinking = value;
            return this;
        }

        private EngineFixture withRunLogger(RunLogger value) {
            runLogger = value;
            return this;
        }

        private RunResult run(ModelProvider provider, String taskId, String goal) {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AgentEngine engine = new AgentEngine(provider, registry, store, trace,
                    maxSteps, thinking, runLogger, executor);
            try {
                return engine.run(new Task(taskId, goal));
            } finally {
                engine.shutdown();
            }
        }
    }
}
