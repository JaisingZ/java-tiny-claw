package io.github.tinyclaw.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.provider.ModelUsage;
import io.github.tinyclaw.agent.runtime.ModelCallMetric;
import io.github.tinyclaw.agent.runtime.RunMetrics;
import io.github.tinyclaw.agent.runtime.ToolCallMetric;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class BenchmarkReportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summarizesCaseResultsAndSmoothnessMetrics() {
        BenchmarkReport report = BenchmarkReport.fromResults(Arrays.asList(
                result("one", true, metrics(10, 2, 12, true, true), 2),
                result("two", false, metrics(7, 1, 8, false, false), 4)));

        assertThat(report.totalCases()).isEqualTo(2);
        assertThat(report.passedCases()).isEqualTo(1);
        assertThat(report.successRate()).isEqualTo(50.0d);
        assertThat(report.modelCalls()).isEqualTo(2);
        assertThat(report.modelDurationMillis()).isEqualTo(22L);
        assertThat(report.promptTokens()).isEqualTo(17);
        assertThat(report.completionTokens()).isEqualTo(3);
        assertThat(report.totalTokens()).isEqualTo(20);
        assertThat(report.usageUnavailableCount()).isEqualTo(1);
        assertThat(report.toolCalls()).isEqualTo(2);
        assertThat(report.toolDurationMillis()).isEqualTo(6L);
        assertThat(report.toolOutputBytes()).isEqualTo(10);
        assertThat(report.toolFailureCount()).isEqualTo(1);
        assertThat(report.turnsToSuccess()).isEqualTo(6);
    }

    @Test
    void serializesStableJsonFields() throws Exception {
        BenchmarkReport report = BenchmarkReport.fromResults(Collections.singletonList(
                result("one", true, metrics(10, 2, 12, true, true), 2)));

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(report));

        assertThat(json.get("totalCases").asInt()).isEqualTo(1);
        assertThat(json.get("passedCases").asInt()).isEqualTo(1);
        assertThat(json.get("successRate").asDouble()).isEqualTo(100.0d);
        assertThat(json.get("modelCalls").asInt()).isEqualTo(1);
        assertThat(json.get("modelDurationMillis").asLong()).isEqualTo(11L);
        assertThat(json.get("promptTokens").asInt()).isEqualTo(10);
        assertThat(json.get("completionTokens").asInt()).isEqualTo(2);
        assertThat(json.get("totalTokens").asInt()).isEqualTo(12);
        assertThat(json.get("usageUnavailableCount").asInt()).isEqualTo(0);
        assertThat(json.get("toolCalls").asInt()).isEqualTo(1);
        assertThat(json.get("toolDurationMillis").asLong()).isEqualTo(3L);
        assertThat(json.get("toolOutputBytes").asInt()).isEqualTo(5);
        assertThat(json.get("toolFailureCount").asInt()).isEqualTo(0);
        assertThat(json.get("turnsToSuccess").asInt()).isEqualTo(2);
        assertThat(json.get("results").get(0).get("caseId").asText()).isEqualTo("one");
        assertThat(json.get("results").get(0).get("traceDir").asText()).contains(".tinyclaw");
    }

    private BenchmarkResult result(String id, boolean passed, RunMetrics metrics, int turnsToSuccess) {
        return new BenchmarkResult(id, "case " + id, passed, passed ? "" : "failed", 100L, metrics,
                ".tinyclaw/bench/workspaces/" + id + "/.tinyclaw/traces", "validation", turnsToSuccess);
    }

    private RunMetrics metrics(int promptTokens, int completionTokens, int totalTokens,
            boolean usageAvailable, boolean toolSuccess) {
        return new RunMetrics(
                Collections.singletonList(new ModelCallMetric(DecisionPhase.ACTION, "model-a", 11L, true, null,
                        new ModelUsage(promptTokens, completionTokens, totalTokens), usageAvailable)),
                Collections.singletonList(new ToolCallMetric("bash", 3L, toolSuccess, 5,
                        toolSuccess ? null : "failed")));
    }
}
