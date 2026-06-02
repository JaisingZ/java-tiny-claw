package com.jaising.agent.runtime;

import com.jaising.agent.domain.AgentContext;
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
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
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
 * 主循环运行时。
 * 只负责短期上下文推进、模型决策和工具执行。
 */
public final class AgentEngine {

    private static final Logger logger = LoggerFactory.getLogger(AgentEngine.class);

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;
    private final boolean enableThinking;
    private final RunLogger runLogger;
    private final ExecutorService toolExecutor;

    /**
     * 创建不启用 thinking 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps) {
        this(provider, toolRegistry, maxSteps, false);
    }

    /**
     * 创建可选 thinking 阶段的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking) {
        this(provider, toolRegistry, maxSteps, enableThinking, NoopRunLogger.INSTANCE);
    }

    /**
     * 创建带可读日志输出的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, createToolExecutor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.runLogger = runLogger == null ? NoopRunLogger.INSTANCE : runLogger;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 执行任务直到模型结束、工具失败、provider 失败或达到最大步数。
     */
    public RunResult run(Task task) {
        logger.info("Starting task: {} (ID: {})", task.goal(), task.taskId());
        AgentContext context = AgentContext.create(task);
        while (context.stepCount() < maxSteps) {
            TurnResult turn = runTurn(context);
            if (turn.result() != null) {
                return turn.result();
            }
            context = turn.context();
        }
        return fail(context, "max_steps_exceeded");
    }

    /**
     * 关闭并行工具执行线程池。
     */
    public void shutdown() {
        toolExecutor.shutdown();
    }

    private static ExecutorService createToolExecutor() {
        return Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
    }

    private TurnResult runTurn(AgentContext context) {
        int currentStep = context.stepCount() + 1;
        logger.info("Starting turn {}/{}", currentStep, maxSteps);
        runLogger.turnStarted(currentStep);

        if (enableThinking) {
            try {
                context = runThinkingPhase(context);
            } catch (ProviderCallException ex) {
                return TurnResult.done(fail(context, ex.reason()));
            }
        }

        Decision decision;
        try {
            decision = requestActionDecision(context);
        } catch (ProviderCallException ex) {
            return TurnResult.done(fail(context, ex.reason()));
        }
        return applyDecision(context, decision);
    }

    private AgentContext runThinkingPhase(AgentContext context) {
        runLogger.thinkingStarted();
        ProviderResponse response = invokeProvider(context, DecisionPhase.THINKING,
                Collections.<ToolDefinition>emptyList());

        if (!(response.decision() instanceof ThinkingDecision)) {
            throw new ProviderCallException("unsupported_thinking_decision");
        }

        ThinkingDecision thinking = (ThinkingDecision) response.decision();
        logger.info("Thinking complete in {}ms: {}", response.durationMillis(), thinking.thought());
        runLogger.thinkingCompleted(thinking, response.durationMillis());
        return context.think(thinking.thought());
    }

    private Decision requestActionDecision(AgentContext context) {
        List<ToolDefinition> toolDefinitions = toolRegistry.definitions();
        runLogger.actionStarted(toolDefinitions);
        ProviderResponse response = invokeProvider(context, DecisionPhase.ACTION, toolDefinitions);

        logger.info("Action decision received in {}ms: {}", response.durationMillis(),
                decisionSummary(response.decision()));
        if (response.decision() instanceof ToolDecision) {
            runLogger.toolDecision((ToolDecision) response.decision());
        }
        return response.decision();
    }

    private ProviderResponse invokeProvider(AgentContext context, DecisionPhase phase,
            List<ToolDefinition> availableTools) {
        long start = System.nanoTime();
        Decision decision;
        try {
            decision = provider.decide(context, phase, availableTools);
        } catch (RuntimeException ex) {
            throw new ProviderCallException("provider_error: " + ex.getMessage());
        }
        return new ProviderResponse(decision, elapsedMillis(start));
    }

    private TurnResult applyDecision(AgentContext context, Decision decision) {
        if (decision instanceof FinishDecision) {
            FinishDecision finish = (FinishDecision) decision;
            runLogger.finished(finish);
            return TurnResult.done(RunResult.success(context.stepCount(), context.observations(), finish.answer()));
        }

        if (decision instanceof ToolDecision) {
            return handleToolDecision(context, ((ToolDecision) decision).call());
        }

        if (decision instanceof ParallelToolDecision) {
            return handleParallelToolDecision(context, (ParallelToolDecision) decision);
        }

        return TurnResult.done(fail(context, "unsupported_decision"));
    }

    private TurnResult handleToolDecision(AgentContext context, ToolCall call) {
        logger.info("Executing tool: {} with args: {}", call.toolName(), call.arguments());
        ToolResult toolResult = executeToolCall(context, call);
        if (!toolResult.success()) {
            logger.warn("Tool {} execution failed: {}", call.toolName(), toolResult.errorMessage());
            return TurnResult.done(fail(context, toolResult.errorMessage()));
        }

        logger.info("Tool {} execution successful", call.toolName());
        return TurnResult.next(advanceAndObserve(context, Collections.singletonList(toolResult.output())));
    }

    private TurnResult handleParallelToolDecision(AgentContext context, ParallelToolDecision decision) {
        List<ToolCall> calls = decision.getCalls();
        if (calls.isEmpty()) {
            return TurnResult.next(context.advance());
        }

        Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults =
                new LinkedHashMap<ToolCall, CompletableFuture<ToolResult>>();
        for (ToolCall call : calls) {
            Tool tool = toolRegistry.snapshot().get(call.toolName());
            if (tool == null) {
                return TurnResult.done(fail(context, "Unknown tool: " + call.toolName()));
            }
            if (!tool.isSideEffect()) {
                readOnlyResults.put(call,
                        CompletableFuture.supplyAsync(() -> executeToolCall(context, call), toolExecutor));
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
                    return TurnResult.done(fail(context, "parallel_execution_failed: " + ex.getMessage()));
                }
            } else {
                result = executeToolCall(context, call);
            }

            if (!result.success()) {
                return TurnResult.done(fail(context, result.errorMessage()));
            }
            outputs.add(result.output());
        }

        return TurnResult.next(advanceAndObserve(context, outputs));
    }

    private ToolResult executeToolCall(AgentContext context, ToolCall call) {
        long toolStart = System.nanoTime();
        runLogger.toolStarted(call);
        ToolResult toolResult = toolRegistry.execute(call, context);
        runLogger.toolCompleted(call, toolResult, elapsedMillis(toolStart));
        return toolResult;
    }

    private AgentContext advanceAndObserve(AgentContext context, List<String> outputs) {
        AgentContext nextContext = context.advance();
        if (outputs.isEmpty()) {
            return nextContext;
        }
        return nextContext.observe(joinOutputs(outputs));
    }

    private RunResult fail(AgentContext context, String reason) {
        logger.error("Step failed: {}", reason);
        runLogger.failed(reason);
        return RunResult.failed(context.stepCount(), context.observations(), reason);
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

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

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
            return "ParallelToolDecision calls=" + ((ParallelToolDecision) decision).getCalls();
        }
        return decision.getClass().getSimpleName();
    }

    private static final class TurnResult {
        private final AgentContext context;
        private final RunResult result;

        private TurnResult(AgentContext context, RunResult result) {
            this.context = context;
            this.result = result;
        }

        private static TurnResult next(AgentContext context) {
            return new TurnResult(context, null);
        }

        private static TurnResult done(RunResult result) {
            return new TurnResult(null, result);
        }

        private AgentContext context() {
            return context;
        }

        private RunResult result() {
            return result;
        }
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
