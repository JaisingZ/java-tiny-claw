package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ReviewDecision;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.observability.TraceScope;
import io.github.tinyclaw.agent.observability.TraceSpan;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.provider.ModelUsage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderCallRunnerTest {

    @Test
    void recordsProviderTraceAndMetricForReviewCall() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);
        ProviderCallRunner runner = new ProviderCallRunner(
                responseProvider(response(ReviewDecision.approved("approved"), "model-a", 11, 2, 13)),
                fixedPrompt("system prompt"),
                Path.of("."),
                new ContextCompactor(),
                recorder);
        RunMetricsCollector metrics = new RunMetricsCollector();
        AgentContext context = AgentContext.create(new Task("task-provider-runner", "review"))
                .think("draft");

        ProviderCallResult result;
        try (TraceScope root = recorder.startRoot("agent.run")) {
            try (TraceScope turn = recorder.startChild(root.span(), "turn")) {
                result = runner.invoke(context, DecisionPhase.REVIEW,
                        Collections.<ToolDefinition>emptyList(), metrics, turn.span());
            }
        }

        assertThat(result.decision()).isInstanceOf(ReviewDecision.class);
        assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(metrics.snapshot().modelCalls()).hasSize(1);
        assertThat(metrics.snapshot().modelCalls().get(0).phase()).isEqualTo(DecisionPhase.REVIEW);
        TraceSpan review = exported.get(0).children().get(0).children().get(0);
        assertThat(review.name()).isEqualTo("llm.review");
        assertThat(review.attributes())
                .containsEntry("phase", "REVIEW")
                .containsEntry("tool_count", 0)
                .containsEntry("final_only_action", false)
                .containsEntry("system_prompt_chars", 13)
                .containsEntry("draft_thought_chars", 5)
                .containsEntry("model", "model-a")
                .containsEntry("success", true)
                .containsEntry("prompt_tokens", 11)
                .containsEntry("completion_tokens", 2)
                .containsEntry("total_tokens", 13);
    }

    @Test
    void recordsProviderFailureTraceAndMetric() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);
        ProviderCallRunner runner = new ProviderCallRunner(
                (state, phase, tools, systemPrompt) -> {
                    throw new RuntimeException("boom");
                },
                fixedPrompt("system"),
                Path.of("."),
                new ContextCompactor(),
                recorder);
        RunMetricsCollector metrics = new RunMetricsCollector();

        try (TraceScope root = recorder.startRoot("agent.run")) {
            try (TraceScope turn = recorder.startChild(root.span(), "turn")) {
                try {
                    runner.invoke(AgentContext.create(new Task("task-provider-failure", "fail")),
                            DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList(), metrics, turn.span());
                } catch (ProviderCallException ex) {
                    assertThat(ex.reason()).isEqualTo("provider_error: boom");
                }
            }
        }

        assertThat(metrics.snapshot().modelCalls()).hasSize(1);
        assertThat(metrics.snapshot().modelCalls().get(0).success()).isFalse();
        assertThat(metrics.snapshot().modelCalls().get(0).failureReason()).isEqualTo("provider_error: boom");
        TraceSpan action = exported.get(0).children().get(0).children().get(0);
        assertThat(action.name()).isEqualTo("llm.action");
        assertThat(action.attributes())
                .containsEntry("success", false)
                .containsEntry("error", "provider_error: boom");
    }

    private static ModelProvider responseProvider(ModelResponse response) {
        return (state, phase, tools, systemPrompt) -> response;
    }

    private static ModelResponse response(ReviewDecision decision, String model, int promptTokens,
            int completionTokens, int totalTokens) {
        return new ModelResponse(decision, new ModelUsage(promptTokens, completionTokens, totalTokens), model, true);
    }

    private static PromptComposer fixedPrompt(String prompt) {
        return new PromptComposer() {
            @Override
            public String compose(PromptContext context) {
                return prompt;
            }
        };
    }
}
