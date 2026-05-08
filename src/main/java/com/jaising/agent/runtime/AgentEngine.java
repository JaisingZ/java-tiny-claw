package com.jaising.agent.runtime;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
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

public final class AgentEngine {

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final List<ToolMiddleware> middleware;
    private final StateStore stateStore;
    private final TraceRecorder traceRecorder;
    private final int maxSteps;

    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry,
                       List<? extends ToolMiddleware> middleware, StateStore stateStore,
                       TraceRecorder traceRecorder, int maxSteps) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.middleware = Collections.unmodifiableList(new ArrayList<ToolMiddleware>(middleware));
        this.stateStore = stateStore;
        this.traceRecorder = traceRecorder;
        this.maxSteps = maxSteps;
    }

    public RunResult run(Task task) {
        AgentState state = stateStore.load(task.taskId()).orElse(AgentState.create(task));
        if (state.status() != AgentStatus.RUNNING) {
            return new RunResult(state);
        }

        while (state.status() == AgentStatus.RUNNING && state.stepCount() < maxSteps) {
            long modelStart = System.nanoTime();
            traceRecorder.record(new TraceEvent(TraceEventType.MODEL_REQUEST, state.taskId(),
                    state.stepCount(), state.goal(), 0L));

            Decision decision;
            try {
                decision = provider.decide(state);
            } catch (RuntimeException ex) {
                state = state.fail("provider_error: " + ex.getMessage());
                stateStore.save(state);
                traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                        state.stepCount(), state.failureReason(), 0L));
                return new RunResult(state);
            }

            long modelDuration = elapsedMillis(modelStart);
            traceRecorder.record(new TraceEvent(TraceEventType.MODEL_RESPONSE, state.taskId(),
                    state.stepCount(), decision.getClass().getSimpleName(), modelDuration));

            if (decision instanceof FinishDecision) {
                FinishDecision finish = (FinishDecision) decision;
                state = state.finish(finish.answer());
                stateStore.save(state);
                traceRecorder.record(new TraceEvent(TraceEventType.FINISHED, state.taskId(),
                        state.stepCount(), finish.answer(), 0L));
                return new RunResult(state);
            }

            if (decision instanceof ToolDecision) {
                ToolDecision toolDecision = (ToolDecision) decision;
                MiddlewareDecision middlewareDecision = checkMiddleware(state, toolDecision.call());
                if (!middlewareDecision.allowed()) {
                    state = state.fail(middlewareDecision.reason());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), middlewareDecision.reason(), 0L));
                    return new RunResult(state);
                }

                Tool tool;
                try {
                    tool = toolRegistry.require(toolDecision.call().toolName());
                } catch (RuntimeException ex) {
                    state = state.fail(ex.getMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), ex.getMessage(), 0L));
                    return new RunResult(state);
                }

                long toolStart = System.nanoTime();
                traceRecorder.record(new TraceEvent(TraceEventType.TOOL_CALL, state.taskId(),
                        state.stepCount(), toolDecision.call().toolName(), 0L));

                ToolResult toolResult;
                try {
                    toolResult = tool.execute(toolDecision.call(), state);
                } catch (RuntimeException ex) {
                    state = state.fail("tool_error: " + ex.getMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), state.failureReason(), 0L));
                    return new RunResult(state);
                }

                long toolDuration = elapsedMillis(toolStart);
                traceRecorder.record(new TraceEvent(TraceEventType.TOOL_RESULT, state.taskId(),
                        state.stepCount(), toolResult.success() ? toolResult.output() : toolResult.errorMessage(),
                        toolDuration));

                if (!toolResult.success()) {
                    state = state.fail(toolResult.errorMessage());
                    stateStore.save(state);
                    traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                            state.stepCount(), toolResult.errorMessage(), 0L));
                    return new RunResult(state);
                }

                state = state.advance().observe(toolResult.output());
                stateStore.save(state);
                continue;
            }

            state = state.fail("unsupported_decision");
            stateStore.save(state);
            traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                    state.stepCount(), "unsupported_decision", 0L));
            return new RunResult(state);
        }

        if (state.status() == AgentStatus.RUNNING) {
            state = state.fail("max_steps_exceeded");
            stateStore.save(state);
            traceRecorder.record(new TraceEvent(TraceEventType.FAILED, state.taskId(),
                    state.stepCount(), "max_steps_exceeded", 0L));
        }

        return new RunResult(state);
    }

    private MiddlewareDecision checkMiddleware(AgentState state, ToolCall call) {
        for (ToolMiddleware toolMiddleware : middleware) {
            MiddlewareDecision decision = toolMiddleware.beforeTool(state, call);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return MiddlewareDecision.allow();
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
