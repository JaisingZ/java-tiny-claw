package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ParallelToolDecision;
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
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.provider.ModelUsage;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主循环运行时。
 * 只负责短期上下文推进、模型决策和工具执行。
 */
public final class AgentEngine {

    private final ModelProvider provider;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;
    private final boolean enableThinking;
    private final RunLogger runLogger;
    private final ExecutorService toolExecutor;
    private final PromptComposer promptComposer;
    private final Path workDir;
    private final ContextCompactor contextCompactor;
    private final ErrorRecoveryAdvisor errorRecoveryAdvisor;
    private final TraceRecorder traceRecorder;

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

    /**
     * 创建带 Prompt 组装器和可读日志输出的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, PromptComposer promptComposer, Path workDir) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, promptComposer, workDir,
                TraceRecorder.noop());
    }

    /**
     * 创建带 Prompt 组装器、可读日志输出和结构化 Trace 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, PromptComposer promptComposer, Path workDir, TraceRecorder traceRecorder) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, createToolExecutor(),
                promptComposer, workDir, traceRecorder);
    }

    /**
     * 创建带 Plan Mode 状态目录的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, Path workDir, boolean planMode, Path stateDir) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, workDir, planMode, stateDir,
                TraceRecorder.noop());
    }

    /**
     * 创建带 Plan Mode 状态目录和结构化 Trace 的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, Path workDir, boolean planMode, Path stateDir, TraceRecorder traceRecorder) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, createToolExecutor(),
                new DefaultPromptComposer(workDir == null ? Path.of(".") : workDir, planMode, stateDir),
                workDir == null ? Path.of(".") : workDir, traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), new ContextCompactor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, ContextCompactor contextCompactor) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor, contextCompactor,
                TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, ContextCompactor contextCompactor,
            TraceRecorder traceRecorder) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), contextCompactor, traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, new ContextCompactor(), TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            TraceRecorder traceRecorder) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, new ContextCompactor(), traceRecorder);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, contextCompactor, TraceRecorder.noop());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor, TraceRecorder traceRecorder) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.runLogger = runLogger == null ? NoopRunLogger.INSTANCE : runLogger;
        this.toolExecutor = toolExecutor;
        this.promptComposer = promptComposer == null ? new DefaultPromptComposer(Path.of(".")) : promptComposer;
        this.workDir = workDir == null ? Path.of(".") : workDir;
        this.contextCompactor = contextCompactor == null ? new ContextCompactor() : contextCompactor;
        this.errorRecoveryAdvisor = new ErrorRecoveryAdvisor();
        this.traceRecorder = traceRecorder == null ? TraceRecorder.noop() : traceRecorder;
    }

    /**
     * 执行任务直到模型结束、工具失败、provider 失败或达到最大步数。
     */
    public RunResult run(Task task) {
        AgentContext context = AgentContext.create(task);
        return runContext(context, new RunMetricsCollector());
    }

    /**
     * 在指定 Session 中执行任务，并把本轮输入、观测和成功回答写回 Session。
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
            rootSpan.putAttribute("max_steps", maxSteps);
            rootSpan.putAttribute("enable_thinking", enableThinking);
            rootSpan.putAttribute("goal_preview", preview(context.goal(), 160));

            SystemReminderInjector systemReminderInjector = new SystemReminderInjector();
            RunResult result = null;
            while (context.stepCount() < maxSteps) {
                TurnResult turn = runTurn(context, systemReminderInjector, metrics, rootSpan);
                if (turn.result() != null) {
                    result = turn.result();
                    break;
                }
                context = turn.context();
            }
            if (result == null) {
                result = fail(context, "max_steps_exceeded", metrics);
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
            RunMetricsCollector metrics, TraceSpan rootSpan) {
        int currentStep = context.stepCount() + 1;
        try (TraceScope turnScope = traceRecorder.startChild(rootSpan, "turn")) {
            TraceSpan turnSpan = turnScope.span();
            turnSpan.putAttribute("step", currentStep);
            turnSpan.putAttribute("observation_count", context.observations().size());
            turnSpan.putAttribute("working_memory_count", context.workingMemory().size());
            runLogger.turnStarted(currentStep);

            if (enableThinking) {
                try {
                    context = runThinkingPhase(context, metrics, turnSpan);
                } catch (ProviderCallException ex) {
                    return TurnResult.done(fail(context, ex.reason(), metrics));
                }
            }

            Decision decision;
            try {
                decision = requestActionDecision(context, metrics, turnSpan);
            } catch (ProviderCallException ex) {
                return TurnResult.done(fail(context, ex.reason(), metrics));
            }
            return applyDecision(context, decision, systemReminderInjector, metrics, turnSpan);
        }
    }

    private AgentContext runThinkingPhase(AgentContext context, RunMetricsCollector metrics, TraceSpan turnSpan) {
        runLogger.thinkingStarted();
        ProviderResponse response = invokeProvider(context, DecisionPhase.THINKING,
                Collections.<ToolDefinition>emptyList(), metrics, turnSpan);

        if (!(response.decision() instanceof ThinkingDecision)) {
            throw new ProviderCallException("unsupported_thinking_decision");
        }

        ThinkingDecision thinking = (ThinkingDecision) response.decision();
        runLogger.thinkingCompleted(thinking, response.durationMillis());
        return context.think(thinking.thought());
    }

    private Decision requestActionDecision(AgentContext context, RunMetricsCollector metrics, TraceSpan turnSpan) {
        List<ToolDefinition> toolDefinitions = toolRegistry.definitions();
        runLogger.actionStarted(toolDefinitions);
        ProviderResponse response = invokeProvider(context, DecisionPhase.ACTION, toolDefinitions, metrics, turnSpan);

        if (response.decision() instanceof ToolDecision) {
            runLogger.toolDecision((ToolDecision) response.decision());
        }
        return response.decision();
    }

    private ProviderResponse invokeProvider(AgentContext context, DecisionPhase phase,
            List<ToolDefinition> availableTools, RunMetricsCollector metrics, TraceSpan turnSpan) {
        long start = System.nanoTime();
        String spanName = phase == DecisionPhase.THINKING ? "llm.thinking" : "llm.action";
        try (TraceScope providerScope = traceRecorder.startChild(turnSpan, spanName)) {
            TraceSpan providerSpan = providerScope.span();
            providerSpan.putAttribute("phase", phase.name());
            providerSpan.putAttribute("tool_count", availableTools.size());
            String systemPrompt = promptComposer.compose(new PromptContext(workDir, phase, availableTools));
            AgentContext compactedContext = contextCompactor.compact(context);
            providerSpan.putAttribute("system_prompt_chars", lengthOf(systemPrompt));
            providerSpan.putAttribute("input_context_message_count", contextMessageCount(compactedContext));
            ModelResponse response = provider.decide(compactedContext, phase, availableTools, systemPrompt);
            long durationMillis = elapsedMillis(start);
            providerSpan.putAttribute("model", response.model());
            providerSpan.putAttribute("success", true);
            providerSpan.putAttribute("usage_available", response.usageAvailable());
            providerSpan.putAttribute("prompt_tokens", response.usage().promptTokens());
            providerSpan.putAttribute("completion_tokens", response.usage().completionTokens());
            providerSpan.putAttribute("total_tokens", response.usage().totalTokens());
            metrics.modelCalls.add(new ModelCallMetric(phase, response.model(), durationMillis, true, null,
                    response.usage(), response.usageAvailable()));
            return new ProviderResponse(response.decision(), durationMillis);
        } catch (RuntimeException ex) {
            String reason = "provider_error: " + ex.getMessage();
            TraceSpan failedSpan = latestChild(turnSpan, spanName);
            if (failedSpan != null) {
                failedSpan.putAttribute("success", false);
                failedSpan.putAttribute("error", reason);
            }
            metrics.modelCalls.add(new ModelCallMetric(phase, "", elapsedMillis(start), false, reason,
                    ModelUsage.empty(), false));
            throw new ProviderCallException(reason);
        }
    }

    private TurnResult applyDecision(AgentContext context, Decision decision,
            SystemReminderInjector systemReminderInjector, RunMetricsCollector metrics, TraceSpan turnSpan) {
        if (decision instanceof FinishDecision) {
            FinishDecision finish = (FinishDecision) decision;
            runLogger.finished(finish);
            return TurnResult.done(RunResult.success(context.stepCount(), context.observations(), finish.answer(),
                    metrics.snapshot()));
        }

        if (decision instanceof ToolDecision) {
            return handleToolDecision(context, ((ToolDecision) decision).call(), systemReminderInjector, metrics,
                    turnSpan);
        }

        if (decision instanceof ParallelToolDecision) {
            return handleParallelToolDecision(context, (ParallelToolDecision) decision, systemReminderInjector,
                    metrics, turnSpan);
        }

        return TurnResult.done(fail(context, "unsupported_decision", metrics));
    }

    private TurnResult handleToolDecision(AgentContext context, ToolCall call,
            SystemReminderInjector systemReminderInjector, RunMetricsCollector metrics, TraceSpan turnSpan) {
        ToolResult toolResult = executeToolCall(context, call, metrics, turnSpan);
        List<String> outputs = new ArrayList<String>();
        outputs.add(observationFor(call, toolResult));
        appendReminder(outputs, systemReminderInjector.afterToolCall(call, toolResult));
        return TurnResult.next(advanceAndObserve(context, outputs));
    }

    private TurnResult handleParallelToolDecision(AgentContext context, ParallelToolDecision decision,
            SystemReminderInjector systemReminderInjector, RunMetricsCollector metrics, TraceSpan turnSpan) {
        List<ToolCall> calls = decision.getCalls();
        if (calls.isEmpty()) {
            return TurnResult.next(context.advance());
        }

        Map<ToolCall, CompletableFuture<ToolResult>> readOnlyResults =
                new LinkedHashMap<ToolCall, CompletableFuture<ToolResult>>();
        for (ToolCall call : calls) {
            Tool tool = toolRegistry.snapshot().get(call.toolName());
            if (tool == null) {
                continue;
            }
            if (!tool.isSideEffect()) {
                readOnlyResults.put(call,
                        CompletableFuture.supplyAsync(() -> executeToolCall(context, call, metrics, turnSpan),
                                toolExecutor));
            }
        }

        List<String> outputs = new ArrayList<String>();
        String lastReminder = null;
        for (ToolCall call : calls) {
            ToolResult result;
            CompletableFuture<ToolResult> future = readOnlyResults.get(call);
            if (future != null) {
                try {
                    result = future.get();
                } catch (Exception ex) {
                    return TurnResult.done(fail(context, "parallel_execution_failed: " + ex.getMessage(), metrics));
                }
            } else {
                result = executeToolCall(context, call, metrics, turnSpan);
            }

            outputs.add(observationFor(call, result));
            String reminder = systemReminderInjector.afterToolCall(call, result);
            if (reminder != null) {
                lastReminder = reminder;
            }
        }

        appendReminder(outputs, lastReminder);
        return TurnResult.next(advanceAndObserve(context, outputs));
    }

    private ToolResult executeToolCall(AgentContext context, ToolCall call, RunMetricsCollector metrics,
            TraceSpan turnSpan) {
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
            runLogger.toolCompleted(call, toolResult, durationMillis);
            toolSpan.putAttribute("success", toolResult.success());
            toolSpan.putAttribute("output_bytes", outputBytes(toolResult));
            if (!toolResult.success()) {
                toolSpan.putAttribute("error", toolResult.errorMessage());
            }
            metrics.toolCalls.add(new ToolCallMetric(call.toolName(), durationMillis, toolResult.success(),
                    outputBytes(toolResult), toolResult.errorMessage()));
            return toolResult;
        }
    }

    private String observationFor(ToolCall call, ToolResult toolResult) {
        if (toolResult.success()) {
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
        return nextContext.observe(joinOutputs(outputs));
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

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private int outputBytes(ToolResult result) {
        if (result == null || !result.success() || result.output() == null) {
            return 0;
        }
        return result.output().getBytes(StandardCharsets.UTF_8).length;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private int contextMessageCount(AgentContext context) {
        int count = 1 + context.workingMemory().size() + context.observations().size();
        if (context.lastThought() != null && !context.lastThought().isBlank()) {
            count++;
        }
        return count;
    }

    private TraceSpan latestChild(TraceSpan parent, String name) {
        List<TraceSpan> children = parent.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            TraceSpan child = children.get(i);
            if (name.equals(child.name())) {
                return child;
            }
        }
        return null;
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

    private static final class RunMetricsCollector {
        private final List<ModelCallMetric> modelCalls =
                Collections.synchronizedList(new ArrayList<ModelCallMetric>());
        private final List<ToolCallMetric> toolCalls =
                Collections.synchronizedList(new ArrayList<ToolCallMetric>());

        private RunMetrics snapshot() {
            synchronized (modelCalls) {
                synchronized (toolCalls) {
                    return new RunMetrics(modelCalls, toolCalls);
                }
            }
        }
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
