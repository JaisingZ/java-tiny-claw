package com.jaising.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.middleware.AllowAllMiddleware;
import com.jaising.agent.middleware.AllowListMiddleware;
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
import java.util.HashSet;
import java.util.List;
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
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        ScriptedProvider provider = new ScriptedProvider();
        AgentEngine engine = new AgentEngine(provider, registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-1", "echo once"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().finalAnswer()).isEqualTo("done");
        assertThat(result.state().observations()).containsExactly("hello");
        assertThat(store.load("task-1")).isPresent();
        assertThat(store.load("task-1").orElseThrow().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(trace.events()).extracting(event -> event.type()).contains(
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
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        AgentEngine engine = new AgentEngine(new ScriptedProvider(), registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4, false);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-1-disabled", "echo once"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(trace.events()).extracting(event -> event.type()).doesNotContain(
                TraceEventType.THINKING_REQUEST,
                TraceEventType.THINKING_RESPONSE);
    }

    /**
     * 找不到工具时失败
     */
    @Test
    void failsWhenToolIsMissing() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();

        AgentEngine engine = new AgentEngine(new MissingToolProvider(), registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-2", "missing tool"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("Unknown tool: missing");
    }

    /**
     * 中间件拒绝时失败
     */
    @Test
    void failsWhenMiddlewareRejectsTool() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        AgentEngine engine = new AgentEngine(new ToolOnlyProvider(), registry,
                Arrays.asList(new AllowListMiddleware(new HashSet<String>(Collections.singleton("noop")))),
                store, trace, 4);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-3", "blocked"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("Tool not allowed: echo");
    }

    /**
     * 达到步数上限时失败
     */
    @Test
    void failsWhenMaxStepsIsExceeded() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        AgentEngine engine = new AgentEngine(new ToolOnlyProvider(), registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 1);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-4", "loop forever"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("max_steps_exceeded");
        assertThat(result.state().stepCount()).isEqualTo(1);
    }

    /**
     * 开启慢思考时 每轮先思考 再行动
     */
    @Test
    void runsThinkingBeforeActionWhenEnabled() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        ThinkingScriptedProvider provider = new ThinkingScriptedProvider();
        AgentEngine engine = new AgentEngine(provider, registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4, true);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-5", "echo once"));

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
        assertThat(trace.events()).extracting(event -> event.type()).containsSubsequence(
                TraceEventType.THINKING_REQUEST,
                TraceEventType.THINKING_RESPONSE,
                TraceEventType.MODEL_REQUEST,
                TraceEventType.MODEL_RESPONSE,
                TraceEventType.TOOL_CALL,
                TraceEventType.TOOL_RESULT,
                TraceEventType.FINISHED);
        assertThat(trace.events())
                .filteredOn(event -> event.type() == TraceEventType.THINKING_RESPONSE)
                .extracting(TraceEvent::detail)
                .containsExactly("plan to call echo", "plan to finish");
    }

    /**
     * 慢思考阶段模型异常直接失败
     */
    @Test
    void failsWhenThinkingProviderThrows() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();

        AgentEngine engine = new AgentEngine(new ThrowingThinkingProvider(), registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4, true);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-6", "think fails"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("provider_error: boom");
    }

    /**
     * 慢思考阶段不允许直接调用工具
     */
    @Test
    void failsWhenThinkingReturnsToolDecision() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        AgentEngine engine = new AgentEngine(new ToolDuringThinkingProvider(), registry,
                Arrays.asList(new AllowAllMiddleware()), store, trace, 4, true);

        com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-7", "bad thinking"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.state().failureReason()).isEqualTo("unsupported_thinking_decision");
        assertThat(result.state().observations()).isEmpty();
    }

    /**
     * 第一次返回工具调用
     * 第二次返回结束决策
     */
    private static final class ScriptedProvider implements ModelProvider {
        /**
         * 根据状态和阶段返回模型决策。
         */
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            if (state.observations().isEmpty()) {
                return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
            }
            return new FinishDecision("done");
        }
    }

    /**
     * 永远返回缺失工具
     */
    private static final class MissingToolProvider implements ModelProvider {
        /**
         * 根据状态和阶段返回模型决策。
         */
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new ToolDecision(new ToolCall("missing", Collections.<String, Object>emptyMap()));
        }
    }

    /**
     * 永远返回 echo 工具
     */
    private static final class ToolOnlyProvider implements ModelProvider {
        /**
         * 根据状态和阶段返回模型决策。
         */
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
        }
    }

    /**
     * 按阶段返回思考 行动和结束
     */
    private static final class ThinkingScriptedProvider implements ModelProvider {
        private final List<DecisionPhase> phases = new ArrayList<DecisionPhase>();
        private final List<List<ToolDefinition>> toolsByPhase = new ArrayList<List<ToolDefinition>>();

        /**
         * 根据状态和阶段返回模型决策。
         */
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
                return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
            }
            return new FinishDecision("done");
        }

        List<DecisionPhase> phases() {
            return phases;
        }

        List<List<ToolDefinition>> toolsByPhase() {
            return toolsByPhase;
        }
    }

    /**
     * 思考阶段抛异常
     */
    private static final class ThrowingThinkingProvider implements ModelProvider {
        /**
         * 根据状态和阶段返回模型决策。
         */
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            if (phase == DecisionPhase.THINKING) {
                throw new RuntimeException("boom");
            }
            return new FinishDecision("unused");
        }
    }

    /**
     * 思考阶段错误地请求工具
     */
    private static final class ToolDuringThinkingProvider implements ModelProvider {
        /**
         * 根据状态和阶段返回模型决策。
         */
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
        }
    }

    /**
     * 简单回显工具
     */
    private static final class EchoTool implements Tool {
        /**
         * 返回工具名称。
         */
        @Override
        public String name() {
            return "echo";
        }

        /**
         * 执行工具调用。
         */
        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success(String.valueOf(call.arguments().get("text")));
        }
    }
}
