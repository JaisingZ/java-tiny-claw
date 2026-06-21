package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.observability.TraceScope;
import io.github.tinyclaw.agent.observability.TraceSpan;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.provider.ModelUsage;
import java.nio.file.Path;
import java.util.List;

/**
 * 封装一次 LLM Provider 调用的上下文准备、trace 和指标记录。
 */
final class ProviderCallRunner {

    private final ModelProvider provider;
    private final PromptComposer promptComposer;
    private final Path workDir;
    private final ContextCompactor contextCompactor;
    private final TraceRecorder traceRecorder;

    ProviderCallRunner(ModelProvider provider, PromptComposer promptComposer, Path workDir,
            ContextCompactor contextCompactor, TraceRecorder traceRecorder) {
        this.provider = provider;
        this.promptComposer = promptComposer;
        this.workDir = workDir;
        this.contextCompactor = contextCompactor;
        this.traceRecorder = traceRecorder;
    }

    ProviderCallResult invoke(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
            RunMetricsCollector metrics, TraceSpan turnSpan) {
        long start = System.nanoTime();
        String spanName = spanName(phase);
        TraceSpan providerSpan = null;
        try (TraceScope providerScope = traceRecorder.startChild(turnSpan, spanName)) {
            providerSpan = providerScope.span();
            recordPhaseAttributes(providerSpan, phase, availableTools);
            String systemPrompt = promptComposer.compose(new PromptContext(workDir, phase, availableTools));
            AgentContext compactedContext = contextCompactor.compact(context);
            recordContextAttributes(providerSpan, systemPrompt, compactedContext);
            ModelResponse response = provider.decide(compactedContext, phase, availableTools, systemPrompt);
            long durationMillis = elapsedMillis(start);
            recordSuccess(providerSpan, response);
            metrics.recordModelCall(new ModelCallMetric(phase, response.model(), durationMillis, true, null,
                    response.usage(), response.usageAvailable()));
            return new ProviderCallResult(response.decision(), durationMillis);
        } catch (RuntimeException ex) {
            String reason = "provider_error: " + ex.getMessage();
            if (providerSpan != null) {
                providerSpan.putAttribute("success", false);
                providerSpan.putAttribute("error", reason);
            }
            metrics.recordModelCall(new ModelCallMetric(phase, "", elapsedMillis(start), false, reason,
                    ModelUsage.empty(), false));
            throw new ProviderCallException(reason);
        }
    }

    private void recordPhaseAttributes(TraceSpan span, DecisionPhase phase, List<ToolDefinition> availableTools) {
        span.putAttribute("phase", phase.name());
        span.putAttribute("tool_count", availableTools.size());
        span.putAttribute("final_only_action", phase == DecisionPhase.ACTION && availableTools.isEmpty());
    }

    private void recordContextAttributes(TraceSpan span, String systemPrompt, AgentContext compactedContext) {
        span.putAttribute("system_prompt_chars", lengthOf(systemPrompt));
        span.putAttribute("input_context_message_count", contextMessageCount(compactedContext));
        span.putAttribute("draft_thought_chars", lengthOf(compactedContext.draftThought()));
        span.putAttribute("review_feedback_chars", lengthOf(compactedContext.reviewFeedback()));
        span.putAttribute("approved_plan_chars", lengthOf(compactedContext.approvedPlan()));
    }

    private void recordSuccess(TraceSpan span, ModelResponse response) {
        span.putAttribute("model", response.model());
        span.putAttribute("success", true);
        span.putAttribute("usage_available", response.usageAvailable());
        span.putAttribute("prompt_tokens", response.usage().promptTokens());
        span.putAttribute("completion_tokens", response.usage().completionTokens());
        span.putAttribute("total_tokens", response.usage().totalTokens());
    }

    private static String spanName(DecisionPhase phase) {
        if (phase == DecisionPhase.THINKING) {
            return "llm.thinking";
        }
        if (phase == DecisionPhase.REVIEW) {
            return "llm.review";
        }
        return "llm.action";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static int contextMessageCount(AgentContext context) {
        int count = 1 + context.workingMemory().size() + context.observations().size();
        if (context.draftThought() != null && !context.draftThought().isBlank()) {
            count++;
        }
        if (context.reviewFeedback() != null && !context.reviewFeedback().isBlank()) {
            count++;
        }
        if (context.approvedPlan() != null && !context.approvedPlan().isBlank()) {
            count++;
        }
        return count;
    }
}
