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
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
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
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, createToolExecutor(),
                promptComposer, workDir);
    }

    /**
     * 创建带 Plan Mode 状态目录的主循环。
     */
    public AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, Path workDir, boolean planMode, Path stateDir) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, createToolExecutor(),
                new DefaultPromptComposer(workDir == null ? Path.of(".") : workDir, planMode, stateDir),
                workDir == null ? Path.of(".") : workDir);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), new ContextCompactor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, ContextCompactor contextCompactor) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                new DefaultPromptComposer(Path.of(".")), Path.of("."), contextCompactor);
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir) {
        this(provider, toolRegistry, maxSteps, enableThinking, runLogger, toolExecutor,
                promptComposer, workDir, new ContextCompactor());
    }

    AgentEngine(ModelProvider provider, ToolRegistry toolRegistry, int maxSteps, boolean enableThinking,
            RunLogger runLogger, ExecutorService toolExecutor, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.runLogger = runLogger == null ? NoopRunLogger.INSTANCE : runLogger;
        this.toolExecutor = toolExecutor;
        this.promptComposer = promptComposer == null ? new DefaultPromptComposer(Path.of(".")) : promptComposer;
        this.workDir = workDir == null ? Path.of(".") : workDir;
        this.contextCompactor = contextCompactor == null ? new ContextCompactor() : contextCompactor;
    }

    /**
     * 执行任务直到模型结束、工具失败、provider 失败或达到最大步数。
     */
    public RunResult run(Task task) {
        AgentContext context = AgentContext.create(task);
        return runContext(context);
    }

    /**
     * 在指定 Session 中执行任务，并把本轮输入、观测和成功回答写回 Session。
     */
    public RunResult run(AgentSession session, Task task) {
        AgentContext context = AgentContext.create(task, session.workingMemory());
        RunResult result = runContext(context);
        recordSessionResult(session, task, result);
        return result;
    }

    private RunResult runContext(AgentContext context) {
        while (context.stepCount() < maxSteps) {
            TurnResult turn = runTurn(context);
            if (turn.result() != null) {
                return turn.result();
            }
            context = turn.context();
        }
        return fail(context, "max_steps_exceeded");
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

    private TurnResult runTurn(AgentContext context) {
        int currentStep = context.stepCount() + 1;
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
        runLogger.thinkingCompleted(thinking, response.durationMillis());
        return context.think(thinking.thought());
    }

    private Decision requestActionDecision(AgentContext context) {
        List<ToolDefinition> toolDefinitions = toolRegistry.definitions();
        runLogger.actionStarted(toolDefinitions);
        ProviderResponse response = invokeProvider(context, DecisionPhase.ACTION, toolDefinitions);

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
            String systemPrompt = promptComposer.compose(new PromptContext(workDir, phase, availableTools));
            AgentContext compactedContext = contextCompactor.compact(context);
            decision = provider.decide(compactedContext, phase, availableTools, systemPrompt);
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
        ToolResult toolResult = executeToolCall(context, call);
        if (!toolResult.success()) {
            return TurnResult.done(fail(context, toolResult.errorMessage()));
        }

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
