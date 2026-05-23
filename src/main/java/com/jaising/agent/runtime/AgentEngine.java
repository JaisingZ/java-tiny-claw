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
import com.jaising.agent.middleware.MiddlewareDecision;
import com.jaising.agent.middleware.ToolMiddleware;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.state.StateStore;
import com.jaising.agent.tool.Tool;
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

    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry,
                       List<? extends ToolMiddleware> middleware, StateStore stateStore,
                       TraceRecorder traceRecorder, int maxSteps) {
        this(provider, toolRegistry, middleware, stateStore, traceRecorder, maxSteps, false);
    }

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
                long thinkingStart = System.nanoTime();
                traceRecorder.record(new TraceEvent(TraceEventType.THINKING_REQUEST, state.taskId(),
                        state.stepCount(), state.goal(), 0L));

                Decision thinkingDecision;
                try {
                    thinkingDecision = provider.decide(state, DecisionPhase.THINKING);
                } catch (RuntimeException ex) {
                    state = state.fail("provider_error: " + ex.getMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), state.failureReason(), 0L));
                    return new RunResult(state);
                }

                long thinkingDuration = elapsedMillis(thinkingStart);
                traceRecorder.record(new TraceEvent(TraceEventType.THINKING_RESPONSE, state.taskId(),
                        state.stepCount(), thinkingDecision.getClass().getSimpleName(), thinkingDuration));

                if (!(thinkingDecision instanceof ThinkingDecision)) {
                    state = state.fail("unsupported_thinking_decision");
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), "unsupported_thinking_decision", 0L));
                    return new RunResult(state);
                }

                ThinkingDecision thinking = (ThinkingDecision) thinkingDecision;
                state = state.think(thinking.thought());
                stateStore.save(state);
            }

            // 记录模型请求起点
            long modelStart = System.nanoTime();
            traceRecorder.record(new TraceEvent(TraceEventType.MODEL_REQUEST, state.taskId(),
                    state.stepCount(), state.goal(), 0L));

            Decision decision;
            try {
                // 让模型给出下一步决策
                decision = provider.decide(state, DecisionPhase.ACTION);
            } catch (RuntimeException ex) {
                // 模型异常直接失败
                state = state.fail("provider_error: " + ex.getMessage());
                stateStore.save(state);
                traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                        state.stepCount(), state.failureReason(), 0L));
                return new RunResult(state);
            }

            // 记录模型耗时
            long modelDuration = elapsedMillis(modelStart);
            traceRecorder.record(new TraceEvent(TraceEventType.MODEL_RESPONSE, state.taskId(),
                    state.stepCount(), decision.getClass().getSimpleName(), modelDuration));

            // 结束决策直接收口
            if (decision instanceof FinishDecision) {
                FinishDecision finish = (FinishDecision) decision;
                state = state.finish(finish.answer());
                stateStore.save(state);
                traceRecorder.record(new TraceEvent(TraceEventType.FINISHED, state.taskId(),
                        state.stepCount(), finish.answer(), 0L));
                return new RunResult(state);
            }

            // 工具决策先过中间层
            if (decision instanceof ToolDecision) {
                ToolDecision toolDecision = (ToolDecision) decision;
                MiddlewareDecision middlewareDecision = checkMiddleware(state, toolDecision.call());
                if (!middlewareDecision.allowed()) {
                    // 拦截失败直接终止
                    state = state.fail(middlewareDecision.reason());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), middlewareDecision.reason(), 0L));
                    return new RunResult(state);
                }

                Tool tool;
                try {
                    // 解析目标工具
                    tool = toolRegistry.require(toolDecision.call().toolName());
                } catch (RuntimeException ex) {
                    // 未知工具直接失败
                    state = state.fail(ex.getMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), ex.getMessage(), 0L));
                    return new RunResult(state);
                }

                // 记录工具调用起点
                long toolStart = System.nanoTime();
                traceRecorder.record(new TraceEvent(TraceEventType.TOOL_CALL, state.taskId(),
                        state.stepCount(), toolDecision.call().toolName(), 0L));

                ToolResult toolResult;
                try {
                    // 执行工具并获取结果
                    toolResult = tool.execute(toolDecision.call(), state);
                } catch (RuntimeException ex) {
                    // 工具异常直接失败
                    state = state.fail("tool_error: " + ex.getMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), state.failureReason(), 0L));
                    return new RunResult(state);
                }

                // 记录工具耗时和结果
                long toolDuration = elapsedMillis(toolStart);
                traceRecorder.record(new TraceEvent(TraceEventType.TOOL_RESULT, state.taskId(),
                        state.stepCount(), toolResult.success() ? toolResult.output() : toolResult.errorMessage(),
                        toolDuration));

                if (!toolResult.success()) {
                    // 工具失败直接收口
                    state = state.fail(toolResult.errorMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), toolResult.errorMessage(), 0L));
                    return new RunResult(state);
                }

                // 成功后推进一步并写入观测
                state = state.advance().observe(toolResult.output());
                stateStore.save(state);
                continue;
            }

            // 未支持的决策类型直接失败
            state = state.fail("unsupported_decision");
            stateStore.save(state);
            traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                    state.stepCount(), "unsupported_decision", 0L));
            return new RunResult(state);
        }

        // 到达步数上限后失败
        if (state.status() == AgentStatus.RUNNING) {
            state = state.fail("max_steps_exceeded");
            stateStore.save(state);
            traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                    state.stepCount(), "max_steps_exceeded", 0L));
        }

        return new RunResult(state);
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
}
