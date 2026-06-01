package com.jaising.agent.runtime;

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
import com.jaising.agent.state.StateStore;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
import com.jaising.agent.trace.TraceRecorder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 主循环运行时
 * 负责模型决策 工具执行 状态推进和审计
 */
public final class AgentEngine {

    private static final Logger logger = LoggerFactory.getLogger(AgentEngine.class);

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final StateStore stateStore;
    private final TraceRecorder traceRecorder;
    private final int maxSteps;
    private final boolean enableThinking;
    private final RunLogger runLogger;
    private final ExecutorService toolExecutor;

    /**
     * 创建 AgentEngine。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps) {
        this(provider, toolRegistry, stateStore, traceRecorder, maxSteps, false);
    }

    /**
     * 创建 AgentEngine。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps, boolean enableThinking) {
        this(provider, toolRegistry, stateStore, traceRecorder, maxSteps, enableThinking,
                NoopRunLogger.INSTANCE);
    }

    /**
     * 创建 AgentEngine。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps, boolean enableThinking, RunLogger runLogger) {
        this(provider, toolRegistry, stateStore, traceRecorder, maxSteps, enableThinking,
                runLogger, createToolExecutor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, StateStore stateStore,
            TraceRecorder traceRecorder, int maxSteps, boolean enableThinking, RunLogger runLogger,
            ExecutorService toolExecutor) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.stateStore = stateStore;
        this.traceRecorder = traceRecorder;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.runLogger = runLogger == null ? NoopRunLogger.INSTANCE : runLogger;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 运行一个任务
     * 只在运行态持续推进循环
     */
    public RunResult run(Task task) {
        return runInternal(task);
    }

    private static ExecutorService createToolExecutor() {
        return Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
    }

    private RunResult runInternal(Task task) {
        logger.info("Starting task: {} (ID: {})", task.goal(), task.taskId());
        AgentState state = stateStore.load(task.taskId()).orElse(AgentState.create(task));
        if (state.status() != AgentStatus.RUNNING) {
            return new RunResult(state);
        }

        while (state.status() == AgentStatus.RUNNING && state.stepCount() < maxSteps) {
            state = runTurn(state);
        }

        if (state.status() == AgentStatus.RUNNING) {
            state = fail(state, "max_steps_exceeded");
        }

        return new RunResult(state);
    }

    private AgentState runTurn(AgentState state) {
        int currentStep = state.stepCount() + 1;
        logger.info("Starting turn {}/{}", currentStep, maxSteps);
        runLogger.turnStarted(currentStep);

        if (enableThinking) {
            try {
                state = runThinkingPhase(state);
            } catch (ProviderCallException ex) {
                return fail(state, ex.reason());
            }
            if (state.status() != AgentStatus.RUNNING) {
                return state;
            }
        }

        Decision decision;
        try {
            decision = requestActionDecision(state);
        } catch (ProviderCallException ex) {
            return fail(state, ex.reason());
        }
        return applyDecision(state, decision);
    }

    /**
     * 关闭资源
     */
    public void shutdown() {
        toolExecutor.shutdown();
    }

    /**
     * 执行慢思考阶段
     * 只保存内部思考 不写入观测
     */
    private AgentState runThinkingPhase(AgentState state) {
        runLogger.thinkingStarted();
        ProviderResponse response = invokeProvider(state, DecisionPhase.THINKING, Collections.<ToolDefinition>emptyList(),
                TraceEventType.THINKING_REQUEST, TraceEventType.THINKING_RESPONSE,
                "enableThinking=true phase=THINKING step=" + state.stepCount()
                        + " tools=[] observations=" + state.observations().size()
                        + " goal=" + state.goal());

        if (!(response.decision() instanceof ThinkingDecision)) {
            return fail(state, "unsupported_thinking_decision");
        }

        ThinkingDecision thinking = (ThinkingDecision) response.decision();
        logger.info("Thinking complete in {}ms: {}", response.durationMillis(), thinking.thought());
        runLogger.thinkingCompleted(thinking, response.durationMillis());
        AgentState nextState = state.think(thinking.thought());
        stateStore.save(nextState);
        return nextState;
    }

    /**
     * 请求行动阶段决策
     * 工具定义只在行动阶段可见
     */
    private Decision requestActionDecision(AgentState state) {
        List<ToolDefinition> toolDefinitions = toolRegistry.definitions();
        runLogger.actionStarted(toolDefinitions);
        ProviderResponse response = invokeProvider(state, DecisionPhase.ACTION, toolDefinitions,
                TraceEventType.MODEL_REQUEST, TraceEventType.MODEL_RESPONSE,
                "phase=ACTION step=" + state.stepCount()
                        + " tools=" + toolNames(toolDefinitions)
                        + " observations=" + state.observations().size()
                        + " hasLastThought=" + hasText(state.lastThought())
                        + " goal=" + state.goal());

        logger.info("Action decision received in {}ms: {}", response.durationMillis(),
                decisionSummary(response.decision()));
        if (response.decision() instanceof ToolDecision) {
            runLogger.toolDecision((ToolDecision) response.decision());
        }
        return response.decision();
    }

    private ProviderResponse invokeProvider(AgentState state, DecisionPhase phase,
            List<ToolDefinition> availableTools, TraceEventType requestType, TraceEventType responseType,
            String requestDetail) {
        long start = System.nanoTime();
        traceRecorder.record(new TraceEvent(requestType, state.taskId(), state.stepCount(), requestDetail, 0L));

        Decision decision;
        try {
            decision = provider.decide(state, phase, availableTools);
        } catch (RuntimeException ex) {
            throw new ProviderCallException("provider_error: " + ex.getMessage());
        }

        long durationMillis = elapsedMillis(start);
        traceRecorder.record(new TraceEvent(responseType, state.taskId(), state.stepCount(),
                decisionSummary(decision), durationMillis));
        return new ProviderResponse(decision, durationMillis);
    }

    /**
     * 根据模型决策推进主循环
     */
    private AgentState applyDecision(AgentState state, Decision decision) {
        if (decision instanceof FinishDecision) {
            FinishDecision finish = (FinishDecision) decision;
            AgentState nextState = state.finish(finish.answer());
            stateStore.save(nextState);
            traceRecorder.record(new TraceEvent(TraceEventType.FINISHED, nextState.taskId(),
                    nextState.stepCount(), "answer=" + finish.answer(), 0L));
            runLogger.finished(finish);
            return nextState;
        }

        if (decision instanceof ToolDecision) {
            return handleToolDecision(state, ((ToolDecision) decision).call());
        }

        if (decision instanceof ParallelToolDecision) {
            return handleParallelToolDecision(state, (ParallelToolDecision) decision);
        }

        return fail(state, "unsupported_decision");
    }

    /**
     * 执行工具决策
     * 成功时推进一步并追加观测
     */
    private AgentState handleToolDecision(AgentState state, ToolCall call) {
        logger.info("Executing tool: {} with args: {}", call.toolName(), call.arguments());
        ToolResult toolResult = executeToolCall(state, call);
        if (!toolResult.success()) {
            logger.warn("Tool {} execution failed: {}", call.toolName(), toolResult.errorMessage());
            return fail(state, toolResult.errorMessage());
        }

        logger.info("Tool {} execution successful", call.toolName());
        return advanceAndObserve(state, Collections.singletonList(toolResult.output()));
    }

    /**
     * 处理并行工具决策
     * 只读工具并发执行，涉写工具顺序执行
     */
    private AgentState handleParallelToolDecision(AgentState state, ParallelToolDecision decision) {
        List<ToolCall> calls = decision.getCalls();
        if (calls.isEmpty()) {
            return advanceState(state);
        }

        Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults = new LinkedHashMap<ToolCall, CompletableFuture<ToolResult>>();
        for (ToolCall call : calls) {
            Tool tool = toolRegistry.snapshot().get(call.toolName());
            if (tool == null) {
                return fail(state, "Unknown tool: " + call.toolName());
            }
            if (!tool.isSideEffect()) {
                readOnlyResults.put(call, CompletableFuture.supplyAsync(() -> executeToolCall(state, call), toolExecutor));
            }
        }

        List<String> outputs = new ArrayList<String>();
        for (ToolCall call : calls) {
            ToolResult result;
            CompletableFuture<ToolResult> future = readOnlyResults.get(call);
            if (future != null) {
                try {
                    result = future.get();
                } catch (Exception ex) {
                    return fail(state, "parallel_execution_failed: " + ex.getMessage());
                }
            } else {
                result = executeToolCall(state, call);
            }

            if (!result.success()) {
                return fail(state, result.errorMessage());
            }
            outputs.add(result.output());
        }

        return advanceAndObserve(state, outputs);
    }

    private ToolResult executeToolCall(AgentState state, ToolCall call) {
        long toolStart = System.nanoTime();
        runLogger.toolStarted(call);
        traceRecorder.record(new TraceEvent(TraceEventType.TOOL_CALL, state.taskId(),
                state.stepCount(), "tool=" + call.toolName() + " args=" + call.arguments(), 0L));

        ToolResult toolResult = toolRegistry.execute(call, state);

        long toolDuration = elapsedMillis(toolStart);
        traceRecorder.record(new TraceEvent(TraceEventType.TOOL_RESULT, state.taskId(),
                state.stepCount(), toolResult.success()
                        ? "success=true output=" + toolResult.output()
                        : "success=false error=" + toolResult.errorMessage(),
                toolDuration));
        runLogger.toolCompleted(call, toolResult, toolDuration);

        return toolResult;
    }

    private AgentState advanceState(AgentState state) {
        AgentState nextState = state.advance();
        stateStore.save(nextState);
        return nextState;
    }

    private AgentState advanceAndObserve(AgentState state, List<String> outputs) {
        AgentState nextState = state.advance();
        if (outputs.isEmpty()) {
            stateStore.save(nextState);
            return nextState;
        }
        nextState = nextState.observe(joinOutputs(outputs));
        stateStore.save(nextState);
        return nextState;
    }

    private String joinOutputs(List<String> outputs) {
        StringBuilder combinedOutput = new StringBuilder();
        for (int i = 0; i < outputs.size(); i++) {
            if (i > 0) {
                combinedOutput.append("\n\n");
            }
            combinedOutput.append(outputs.get(i));
        }
        return combinedOutput.toString();
    }

    /**
     * 统一记录失败状态
     */
    private AgentState fail(AgentState state, String reason) {
        logger.error("Step failed: {}", reason);
        AgentState failed = state.fail(reason);
        stateStore.save(failed);
        traceRecorder.record(new TraceEvent(TraceEventType.FAILED, failed.taskId(),
                failed.stepCount(), "reason=" + failed.failureReason(), 0L));
        runLogger.failed(failed.failureReason());
        return failed;
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
        if (decision instanceof ParallelToolDecision) {
            List<ToolCall> calls = ((ParallelToolDecision) decision).getCalls();
            return "ParallelToolDecision calls=" + calls;
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

    private static final class ProviderResponse {
        private final Decision decision;
        private final long durationMillis;

        private ProviderResponse(Decision decision, long durationMillis) {
            this.decision = decision;
            this.durationMillis = durationMillis;
        }

        private Decision decision() {
            return decision;
        }

        private long durationMillis() {
            return durationMillis;
        }
    }

    private static final class ProviderCallException extends RuntimeException {
        private final String reason;

        private ProviderCallException(String reason) {
            super(reason);
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
