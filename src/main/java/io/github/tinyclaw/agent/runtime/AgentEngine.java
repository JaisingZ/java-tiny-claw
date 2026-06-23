package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ReviewDecision;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.observability.TraceScope;
import io.github.tinyclaw.agent.observability.TraceSpan;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主循环运行时。
 * 只负责短期上下文推进、模型决策和工具执行。
 */
public final class AgentEngine {

    private static final int MAX_REVIEW_ATTEMPTS = 2;

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final boolean enableThinking;
    private final RunLogger runLogger;
    private final ExecutorService toolExecutor;
    private final PromptComposer promptComposer;
    private final Path workDir;
    private final ContextCompactor contextCompactor;
    private final ErrorRecoveryAdvisor errorRecoveryAdvisor;
    private final TraceRecorder traceRecorder;
    private final ProviderCallRunner providerCallRunner;
    private final ToolCallRunner toolCallRunner;

    /**
     * 创建不启用 thinking 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry) {
        this(provider, toolRegistry, false);
    }

    /**
     * 创建可选 thinking 阶段的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking) {
        this(provider, toolRegistry, enableThinking, NoopRunLogger.INSTANCE);
    }

    /**
     * 创建带可读日志输出的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger) {
        this(provider, toolRegistry, enableThinking, runLogger, createToolExecutor());
    }

    /**
     * 创建带 Prompt 组装器和可读日志输出的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, PromptComposer promptComposer, Path workDir) {
        this(provider, toolRegistry, enableThinking, runLogger, promptComposer, workDir,
                TraceRecorder.noop());
    }

    /**
     * 创建带 Prompt 组装器、可读日志输出和结构化 Trace 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, PromptComposer promptComposer, Path workDir, TraceRecorder traceRecorder) {
        this(provider, toolRegistry, enableThinking, runLogger, createToolExecutor(),
                promptComposer, workDir, traceRecorder);
    }

    /**
     * 创建带 Plan Mode 状态目录的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, Path workDir, boolean planMode, Path stateDir) {
        this(provider, toolRegistry, enableThinking, runLogger, workDir, planMode, stateDir,
                TraceRecorder.noop());
    }

    /**
     * 创建带 Plan Mode 状态目录和结构化 Trace 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, Path workDir, boolean planMode, Path stateDir, TraceRecorder traceRecorder) {
        this(provider, toolRegistry, enableThinking, runLogger, createToolExecutor(),
                new DefaultPromptComposer(workDir == null ? Path.of(".") : workDir, planMode, stateDir),
                workDir == null ? Path.of(".") : workDir, traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), new ContextCompactor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, ContextCompactor contextCompactor) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor, contextCompactor,
                TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, ContextCompactor contextCompactor,
            TraceRecorder traceRecorder) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), contextCompactor, traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, new ContextCompactor(), TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            TraceRecorder traceRecorder) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, new ContextCompactor(), traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor) {
        this(provider, toolRegistry, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, contextCompactor, TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor, TraceRecorder traceRecorder) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.enableThinking = enableThinking;
        this.runLogger = runLogger == null ? NoopRunLogger.INSTANCE : runLogger;
        this.toolExecutor = toolExecutor;
        this.promptComposer = promptComposer == null ? new DefaultPromptComposer(Path.of(".")) : promptComposer;
        this.workDir = workDir == null ? Path.of(".") : workDir;
        this.contextCompactor = contextCompactor == null ? new ContextCompactor() : contextCompactor;
        this.errorRecoveryAdvisor = new ErrorRecoveryAdvisor();
        this.traceRecorder = traceRecorder == null ? TraceRecorder.noop() : traceRecorder;
        this.providerCallRunner = new ProviderCallRunner(this.provider, this.promptComposer, this.workDir,
                this.contextCompactor, this.traceRecorder);
        this.toolCallRunner = new ToolCallRunner(this.toolRegistry, this.runLogger, this.traceRecorder);
    }

    /**
     * 执行任务直到模型结束、provider 失败或运行时遇到不可恢复错误。
     */
    public RunResult run(Task task) {
        AgentContext context = AgentContext.create(task);
        return runContext(context, new RunMetricsCollector());
    }

    /**
     * 在指定 Session 中执行任务，并把成功轮次的输入、观测和回答写回 Session。
     */
    public RunResult run(AgentSession session, Task task) {
        AgentContext context = AgentContext.create(task, session.workingMemory());
        RunResult result = runContext(context, new RunMetricsCollector());
        recordSessionResult(session, task, result);
        session.record(result.metrics());
        return result;
    }

    private RunResult runContext(AgentContext context, RunMetricsCollector metrics) {
        try (TraceScope rootScope = traceRecorder.startRoot("agent.run")) {
            TraceSpan rootSpan = rootScope.span();
            rootSpan.putAttribute("work_dir", workDir.toString());
            rootSpan.putAttribute("enable_thinking", enableThinking);
            rootSpan.putAttribute("goal_preview", preview(context.goal(), 160));

            SystemReminderInjector systemReminderInjector = new SystemReminderInjector();
            TokenEfficiencyAdvisor tokenEfficiencyAdvisor = new TokenEfficiencyAdvisor();
            RunResult result = null;
            while (true) {
                TurnResult turn = runTurn(context, systemReminderInjector, tokenEfficiencyAdvisor, metrics, rootSpan);
                if (turn.result() != null) {
                    result = turn.result();
                    break;
                }
                context = turn.context();
            }
            rootSpan.putAttribute("success", result.status() == RunStatus.SUCCESS);
            rootSpan.putAttribute("step_count", result.stepCount());
            if (result.status() == RunStatus.FAILED) {
                rootSpan.putAttribute("failure_reason", result.failureReason());
            }
            return result;
        }
    }

    private void recordSessionResult(AgentSession session, Task task, RunResult result) {
        if (result.status() != RunStatus.SUCCESS) {
            return;
        }
        session.append(SessionMessage.user(task.goal()));
        for (String observation : result.observations()) {
            session.append(SessionMessage.observation(observation));
        }
        if (result.status() == RunStatus.SUCCESS) {
            session.append(SessionMessage.assistant(result.finalAnswer()));
        }
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

    private TurnResult runTurn(AgentContext context, SystemReminderInjector systemReminderInjector,
            TokenEfficiencyAdvisor tokenEfficiencyAdvisor, RunMetricsCollector metrics, TraceSpan rootSpan) {
        int currentStep = context.stepCount() + 1;
        try (TraceScope turnScope = traceRecorder.startChild(rootSpan, "turn")) {
            TraceSpan turnSpan = turnScope.span();
            turnSpan.putAttribute("step", currentStep);
            turnSpan.putAttribute("observation_count", context.observations().size());
            turnSpan.putAttribute("working_memory_count", context.workingMemory().size());
            runLogger.turnStarted(currentStep);

            if (enableThinking) {
                try {
                    context = runPlanningReviewLoop(context, metrics, turnSpan);
                } catch (ProviderCallException ex) {
                    return TurnResult.done(fail(context, ex.reason(), metrics));
                }
            }

            Decision decision;
            try {
                decision = requestActionDecision(context, tokenEfficiencyAdvisor, metrics, turnSpan);
            } catch (ProviderCallException ex) {
                return TurnResult.done(fail(context, ex.reason(), metrics));
            }
            return applyDecision(context, decision, systemReminderInjector, tokenEfficiencyAdvisor, metrics, turnSpan);
        }
    }

    private AgentContext runPlanningReviewLoop(AgentContext context, RunMetricsCollector metrics,
            TraceSpan turnSpan) {
        AgentContext current = context;
        for (int attempt = 1; attempt <= MAX_REVIEW_ATTEMPTS; attempt++) {
            current = runThinkingPhase(current, metrics, turnSpan);
            ReviewDecision review = runReviewPhase(current, attempt, metrics, turnSpan);
            if (review.status() == ReviewDecision.Status.APPROVED) {
                if (!hasText(review.approvedPlan())) {
                    throw new ProviderCallException("plan_review_failed");
                }
                return current.withApprovedPlan(review.approvedPlan());
            }
            if (review.status() == ReviewDecision.Status.REVISE) {
                current = current.withReviewFeedback(review.feedback());
                continue;
            }
            if (review.status() == ReviewDecision.Status.BLOCKED) {
                throw new ProviderCallException("plan_review_blocked: " + nullSafe(review.reason()));
            }
            throw new ProviderCallException("unsupported_review_decision");
        }
        throw new ProviderCallException("plan_review_failed");
    }

    private AgentContext runThinkingPhase(AgentContext context, RunMetricsCollector metrics, TraceSpan turnSpan) {
        runLogger.thinkingStarted();
        ProviderCallResult response = providerCallRunner.invoke(context, DecisionPhase.THINKING,
                Collections.<ToolDefinition>emptyList(), metrics, turnSpan);

        if (!(response.decision() instanceof ThinkingDecision)) {
            throw new ProviderCallException("unsupported_thinking_decision");
        }

        ThinkingDecision thinking = (ThinkingDecision) response.decision();
        runLogger.thinkingCompleted(thinking, response.durationMillis());
        return context.think(thinking.thought());
    }

    private ReviewDecision runReviewPhase(AgentContext context, int attempt, RunMetricsCollector metrics,
            TraceSpan turnSpan) {
        runLogger.reviewStarted(attempt);
        ProviderCallResult response = providerCallRunner.invoke(context, DecisionPhase.REVIEW,
                Collections.<ToolDefinition>emptyList(), metrics, turnSpan);

        if (!(response.decision() instanceof ReviewDecision)) {
            throw new ProviderCallException("unsupported_review_decision");
        }

        ReviewDecision review = (ReviewDecision) response.decision();
        runLogger.reviewCompleted(review, response.durationMillis());
        return review;
    }

    private Decision requestActionDecision(AgentContext context, TokenEfficiencyAdvisor tokenEfficiencyAdvisor,
            RunMetricsCollector metrics, TraceSpan turnSpan) {
        List<ToolDefinition> toolDefinitions = actionToolsFor(tokenEfficiencyAdvisor);
        runLogger.actionStarted(toolDefinitions);
        ProviderCallResult response = providerCallRunner.invoke(context, DecisionPhase.ACTION,
                toolDefinitions, metrics, turnSpan);

        if (response.decision() instanceof ToolDecision) {
            runLogger.toolDecision((ToolDecision) response.decision());
        }
        return response.decision();
    }

    private List<ToolDefinition> actionToolsFor(TokenEfficiencyAdvisor tokenEfficiencyAdvisor) {
        if (tokenEfficiencyAdvisor.validationPassed()) {
            return Collections.emptyList();
        }
        return toolRegistry.definitions();
    }

    private TurnResult applyDecision(AgentContext context, Decision decision,
            SystemReminderInjector systemReminderInjector, TokenEfficiencyAdvisor tokenEfficiencyAdvisor,
            RunMetricsCollector metrics, TraceSpan turnSpan) {
        if (decision instanceof FinishDecision) {
            FinishDecision finish = (FinishDecision) decision;
            runLogger.finished(finish);
            return TurnResult.done(RunResult.success(context.stepCount(), context.observations(), finish.answer(),
                    metrics.snapshot()));
        }

        if (decision instanceof ToolDecision) {
            return handleToolDecision(context, (ToolDecision) decision, systemReminderInjector,
                    tokenEfficiencyAdvisor, metrics, turnSpan);
        }

        return TurnResult.done(fail(context, "unsupported_decision", metrics));
    }

    private TurnResult handleToolDecision(AgentContext context, ToolDecision decision,
            SystemReminderInjector systemReminderInjector, TokenEfficiencyAdvisor tokenEfficiencyAdvisor,
            RunMetricsCollector metrics, TraceSpan turnSpan) {
        List<ToolCall> calls = decision.calls();
        if (calls.isEmpty()) {
            return TurnResult.next(context.advance());
        }

        Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults =
                startReadOnlyToolCalls(context, calls, metrics, turnSpan);
        ParallelToolOutputs collected = collectParallelToolOutputs(context, calls, readOnlyResults,
                systemReminderInjector, tokenEfficiencyAdvisor, metrics, turnSpan);
        if (collected.failureReason() != null) {
            return TurnResult.done(fail(context, collected.failureReason(), metrics));
        }
        return TurnResult.next(advanceAndObserve(context, collected.outputs()));
    }

    private Map<ToolCall, CompletableFuture<ToolResult>> startReadOnlyToolCalls(AgentContext context,
            List<ToolCall> calls, RunMetricsCollector metrics, TraceSpan turnSpan) {
        Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults =
                new LinkedHashMap<ToolCall, CompletableFuture<ToolResult>>();
        for (ToolCall call : calls) {
            Tool tool = toolRegistry.snapshot().get(call.toolName());
            if (tool == null) {
                continue;
            }
            if (!tool.isSideEffect()) {
                readOnlyResults.put(call,
                        CompletableFuture.supplyAsync(() -> toolCallRunner.execute(context, call, metrics, turnSpan),
                                toolExecutor));
            }
        }
        return readOnlyResults;
    }

    private ParallelToolOutputs collectParallelToolOutputs(AgentContext context, List<ToolCall> calls,
            Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults,
            SystemReminderInjector systemReminderInjector, TokenEfficiencyAdvisor tokenEfficiencyAdvisor,
            RunMetricsCollector metrics, TraceSpan turnSpan) {
        List<String> outputs = new ArrayList<String>();
        String lastReminder = null;
        String lastTokenReminder = null;
        for (ToolCall call : calls) {
            ToolResult result;
            CompletableFuture<ToolResult> future = readOnlyResults.get(call);
            if (future != null) {
                try {
                    result = future.get();
                } catch (Exception ex) {
                    return ParallelToolOutputs.failed("parallel_execution_failed: " + ex.getMessage());
                }
            } else {
                result = toolCallRunner.execute(context, call, metrics, turnSpan);
            }

            outputs.add(observationFor(call, result));
            String reminder = systemReminderInjector.afterToolCall(call, result);
            if (reminder != null) {
                lastReminder = reminder;
            }
            String tokenReminder = tokenEfficiencyAdvisor.afterToolCall(context, call, result);
            if (tokenReminder != null) {
                lastTokenReminder = tokenReminder;
            }
        }

        appendReminder(outputs, lastReminder);
        appendReminder(outputs, lastTokenReminder);
        return ParallelToolOutputs.success(outputs);
    }

    private String observationFor(ToolCall call, ToolResult toolResult) {
        if (toolResult.success()) {
            if (TokenEfficiencyAdvisor.isReadFile(call)) {
                String path = TokenEfficiencyAdvisor.normalizedPath(call);
                if (path != null) {
                    return "[read_file path=" + path + "]\n" + toolResult.output();
                }
            }
            return toolResult.output();
        }
        return errorRecoveryAdvisor.advise(call, toolResult.errorMessage());
    }

    private void appendReminder(List<String> outputs, String reminder) {
        if (reminder != null) {
            outputs.add(reminder);
        }
    }

    private AgentContext advanceAndObserve(AgentContext context, List<String> outputs) {
        AgentContext nextContext = context.advance();
        if (outputs.isEmpty()) {
            return nextContext;
        }
        String observation = joinOutputs(outputs);
        if (shouldSkipDuplicateObservation(context, observation)) {
            return nextContext;
        }
        return nextContext.observe(observation);
    }

    private boolean shouldSkipDuplicateObservation(AgentContext context, String observation) {
        if (observation.startsWith("Error executing ")
                || observation.contains("\n\nError executing ")
                || observation.contains("[SYSTEM REMINDER]")) {
            return false;
        }
        return context.observations().contains(observation);
    }

    private RunResult fail(AgentContext context, String reason, RunMetricsCollector metrics) {
        runLogger.failed(reason);
        return RunResult.failed(context.stepCount(), context.observations(), reason, metrics.snapshot());
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
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

    private static final class ParallelToolOutputs {
        private final List<String> outputs;
        private final String failureReason;

        private ParallelToolOutputs(List<String> outputs, String failureReason) {
            this.outputs = outputs;
            this.failureReason = failureReason;
        }

        private static ParallelToolOutputs success(List<String> outputs) {
            return new ParallelToolOutputs(outputs, null);
        }

        private static ParallelToolOutputs failed(String failureReason) {
            return new ParallelToolOutputs(Collections.<String>emptyList(), failureReason);
        }

        private List<String> outputs() {
            return outputs;
        }

        private String failureReason() {
            return failureReason;
        }
    }
}
