package com.jaising.agent.runtime;

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
import com.jaising.agent.middleware.MiddlewareDecision;
import com.jaising.agent.middleware.ToolMiddleware;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.state.StateStore;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
import com.jaising.agent.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主循环运行时
 * 负责模型决策 工具执行 状态推进和审计
 */
public final class AgentEngine {

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final List<ToolMiddleware> middleware;
    private final StateStore stateStore;
    private final TraceRecorder traceRecorder;
    private final int maxSteps;
    private final boolean enableThinking;

    /**
     * 创建 AgentEngine。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry,
            List<? extends ToolMiddleware> middleware, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps) {
        this(provider, toolRegistry, middleware, stateStore, traceRecorder, maxSteps, false);
    }

    /**
     * 创建 AgentEngine。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry,
            List<? extends ToolMiddleware> middleware, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps, boolean enableThinking) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.middleware = Collections.unmodifiableList(new ArrayList<ToolMiddleware>(middleware));
        this.stateStore = stateStore;
        this.traceRecorder = traceRecorder;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
    }

    /**
     * 运行一个任务
     * 只在运行态持续推进循环
     */
    public RunResult run(Task task) {
        // 先恢复已有状态
        AgentState state = stateStore.load(task.taskId()).orElse(AgentState.create(task));
        // 非运行态直接返回
        if (state.status() != AgentStatus.RUNNING) {
            return new RunResult(state);
        }

        // 逐轮推进直到结束或超步数
        while (state.status() == AgentStatus.RUNNING && state.stepCount() < maxSteps) {
            if (enableThinking) {
                LoopStep thinkingStep = runThinkingStep(state);
                if (thinkingStep.done()) {
                    return new RunResult(thinkingStep.state());
                }
                state = thinkingStep.state();
            }

            DecisionStep decisionStep = requestActionDecision(state);
            if (decisionStep.done()) {
                return new RunResult(decisionStep.state());
            }

            LoopStep actionStep = handleDecision(decisionStep.state(), decisionStep.decision());
            if (actionStep.done()) {
                return new RunResult(actionStep.state());
            }
            state = actionStep.state();
        }

        // 到达步数上限后失败
        if (state.status() == AgentStatus.RUNNING) {
            state = fail(state, "max_steps_exceeded").state();
        }

        return new RunResult(state);
    }

    /**
     * 执行慢思考阶段
     * 只保存内部思考 不写入观测
     */
    private LoopStep runThinkingStep(AgentState state) {
        long thinkingStart = System.nanoTime();
        traceRecorder.record(new TraceEvent(TraceEventType.THINKING_REQUEST, state.taskId(),
                state.stepCount(), "enableThinking=true phase=THINKING step=" + state.stepCount()
                        + " tools=[] observations=" + state.observations().size()
                        + " goal=" + state.goal(), 0L));

        Decision thinkingDecision;
        try {
            thinkingDecision = provider.decide(state, DecisionPhase.THINKING, Collections.emptyList());
        } catch (RuntimeException ex) {
            return fail(state, "provider_error: " + ex.getMessage());
        }

        long thinkingDuration = elapsedMillis(thinkingStart);
        if (!(thinkingDecision instanceof ThinkingDecision)) {
            traceRecorder.record(new TraceEvent(TraceEventType.THINKING_RESPONSE, state.taskId(),
                    state.stepCount(), decisionSummary(thinkingDecision), thinkingDuration));
            return fail(state, "unsupported_thinking_decision");
        }

        ThinkingDecision thinking = (ThinkingDecision) thinkingDecision;
        traceRecorder.record(new TraceEvent(TraceEventType.THINKING_RESPONSE, state.taskId(),
                state.stepCount(), decisionSummary(thinking), thinkingDuration));
        AgentState nextState = state.think(thinking.thought());
        stateStore.save(nextState);
        return LoopStep.continueWith(nextState);
    }

    /**
     * 请求行动阶段决策
     * 工具定义只在行动阶段可见
     */
    private DecisionStep requestActionDecision(AgentState state) {
        long modelStart = System.nanoTime();
        List<ToolDefinition> toolDefinitions = toolRegistry.definitions();
        traceRecorder.record(new TraceEvent(TraceEventType.MODEL_REQUEST, state.taskId(),
                state.stepCount(), "phase=ACTION step=" + state.stepCount()
                        + " tools=" + toolNames(toolDefinitions)
                        + " observations=" + state.observations().size()
                        + " hasLastThought=" + hasText(state.lastThought())
                        + " goal=" + state.goal(), 0L));

        Decision decision;
        try {
            decision = provider.decide(state, DecisionPhase.ACTION, toolDefinitions);
        } catch (RuntimeException ex) {
            return DecisionStep.done(fail(state, "provider_error: " + ex.getMessage()).state());
        }

        long modelDuration = elapsedMillis(modelStart);
        traceRecorder.record(new TraceEvent(TraceEventType.MODEL_RESPONSE, state.taskId(),
                state.stepCount(), decisionSummary(decision), modelDuration));
        return DecisionStep.continueWith(state, decision);
    }

    /**
     * 根据模型决策推进主循环
     */
    private LoopStep handleDecision(AgentState state, Decision decision) {
        if (decision instanceof FinishDecision) {
            FinishDecision finish = (FinishDecision) decision;
            AgentState nextState = state.finish(finish.answer());
            stateStore.save(nextState);
            traceRecorder.record(new TraceEvent(TraceEventType.FINISHED, nextState.taskId(),
                    nextState.stepCount(), "answer=" + finish.answer(), 0L));
            return LoopStep.done(nextState);
        }

        if (decision instanceof ToolDecision) {
            return handleToolDecision(state, (ToolDecision) decision);
        }

        return fail(state, "unsupported_decision");
    }

    /**
     * 执行工具决策
     * 成功时推进一步并追加观测
     */
    private LoopStep handleToolDecision(AgentState state, ToolDecision toolDecision) {
        MiddlewareDecision middlewareDecision = checkMiddleware(state, toolDecision.call());
        if (!middlewareDecision.allowed()) {
            return fail(state, middlewareDecision.reason());
        }

        long toolStart = System.nanoTime();
        traceRecorder.record(new TraceEvent(TraceEventType.TOOL_CALL, state.taskId(),
                state.stepCount(), "tool=" + toolDecision.call().toolName()
                        + " args=" + toolDecision.call().arguments(), 0L));

        ToolResult toolResult = toolRegistry.execute(toolDecision.call(), state);

        long toolDuration = elapsedMillis(toolStart);
        traceRecorder.record(new TraceEvent(TraceEventType.TOOL_RESULT, state.taskId(),
                state.stepCount(), toolResult.success()
                        ? "success=true output=" + toolResult.output()
                        : "success=false error=" + toolResult.errorMessage(),
                toolDuration));

        if (!toolResult.success()) {
            return fail(state, toolResult.errorMessage());
        }

        AgentState nextState = state.advance().observe(toolResult.output());
        stateStore.save(nextState);
        return LoopStep.continueWith(nextState);
    }

    /**
     * 统一记录失败状态
     */
    private LoopStep fail(AgentState state, String reason) {
        AgentState failed = state.fail(reason);
        stateStore.save(failed);
        traceRecorder.record(new TraceEvent(TraceEventType.FAILED, failed.taskId(),
                failed.stepCount(), "reason=" + failed.failureReason(), 0L));
        return LoopStep.done(failed);
    }

    /**
     * 统一执行工具前检查
     * 任何拒绝都返回第一个原因
     */
    private MiddlewareDecision checkMiddleware(AgentState state, ToolCall call) {
        for (ToolMiddleware toolMiddleware : middleware) {
            MiddlewareDecision decision = toolMiddleware.beforeTool(state, call);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return MiddlewareDecision.allow();
    }

    /**
     * 计算毫秒耗时
     * 仅用于 trace
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 汇总模型决策
     * 用于 trace 打印
     */
    private String decisionSummary(Decision decision) {
        if (decision instanceof ThinkingDecision) {
            return "ThinkingDecision thought=" + ((ThinkingDecision) decision).thought();
        }
        if (decision instanceof FinishDecision) {
            return "FinishDecision answer=" + ((FinishDecision) decision).answer();
        }
        if (decision instanceof ToolDecision) {
            ToolCall call = ((ToolDecision) decision).call();
            return "ToolDecision tool=" + call.toolName() + " args=" + call.arguments();
        }
        return decision.getClass().getSimpleName();
    }

    /**
     * 汇总可见工具名称
     */
    private List<String> toolNames(List<ToolDefinition> definitions) {
        List<String> names = new ArrayList<String>();
        for (ToolDefinition definition : definitions) {
            names.add(definition.name());
        }
        return names;
    }

    /**
     * 判断字符串是否有内容
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 主循环单步结果
     */
    private static final class LoopStep {
        private final AgentState state;
        private final boolean done;

        private LoopStep(AgentState state, boolean done) {
            this.state = state;
            this.done = done;
        }

        private static LoopStep continueWith(AgentState state) {
            return new LoopStep(state, false);
        }

        private static LoopStep done(AgentState state) {
            return new LoopStep(state, true);
        }

        private AgentState state() {
            return state;
        }

        private boolean done() {
            return done;
        }
    }

    /**
     * 行动阶段模型决策结果
     */
    private static final class DecisionStep {
        private final AgentState state;
        private final Decision decision;
        private final boolean done;

        private DecisionStep(AgentState state, Decision decision, boolean done) {
            this.state = state;
            this.decision = decision;
            this.done = done;
        }

        private static DecisionStep continueWith(AgentState state, Decision decision) {
            return new DecisionStep(state, decision, false);
        }

        private static DecisionStep done(AgentState state) {
            return new DecisionStep(state, null, true);
        }

        private AgentState state() {
            return state;
        }

        private Decision decision() {
            return decision;
        }

        private boolean done() {
            return done;
        }
    }
}
