package io.github.tinyclaw.agent.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.tinyclaw.agent.runtime.ToolCallMetric;
import java.util.List;

/**
 * benchmark 结果汇总。
 */
public record BenchmarkReport(List<BenchmarkResult> results) {

    public BenchmarkReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static BenchmarkReport fromResults(List<BenchmarkResult> results) {
        return new BenchmarkReport(results);
    }

    @JsonProperty("results")
    public List<BenchmarkResult> results() {
        return results;
    }

    @JsonProperty("totalCases")
    public int totalCases() {
        return results().size();
    }

    @JsonProperty("passedCases")
    public int passedCases() {
        int count = 0;
        for (BenchmarkResult result : results()) {
            if (result.passed()) {
                count++;
            }
        }
        return count;
    }

    @JsonProperty("successRate")
    public double successRate() {
        int total = totalCases();
        if (total == 0) {
            return 0.0d;
        }
        return (double) passedCases() / total * 100.0d;
    }

    @JsonProperty("modelCalls")
    public int modelCalls() {
        int count = 0;
        for (BenchmarkResult result : results()) {
            count += result.runMetrics().modelCallCount();
        }
        return count;
    }

    @JsonProperty("promptTokens")
    public int promptTokens() {
        int total = 0;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().promptTokens();
        }
        return total;
    }

    @JsonProperty("completionTokens")
    public int completionTokens() {
        int total = 0;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().completionTokens();
        }
        return total;
    }

    @JsonProperty("totalTokens")
    public int totalTokens() {
        int total = 0;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().totalTokens();
        }
        return total;
    }

    @JsonProperty("usageUnavailableCount")
    public int usageUnavailableCount() {
        int count = 0;
        for (BenchmarkResult result : results()) {
            count += result.runMetrics().usageUnavailableCount();
        }
        return count;
    }

    @JsonProperty("modelDurationMillis")
    public long modelDurationMillis() {
        long total = 0L;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().modelDurationMillis();
        }
        return total;
    }

    @JsonProperty("toolCalls")
    public long toolCalls() {
        long count = 0L;
        for (BenchmarkResult result : results()) {
            count += result.runMetrics().toolCallCount();
        }
        return count;
    }

    @JsonProperty("toolDurationMillis")
    public long toolDurationMillis() {
        long total = 0L;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().toolDurationMillis();
        }
        return total;
    }

    @JsonProperty("toolOutputBytes")
    public long toolOutputBytes() {
        long total = 0L;
        for (BenchmarkResult result : results()) {
            total += result.runMetrics().toolOutputBytes();
        }
        return total;
    }

    @JsonProperty("toolFailureCount")
    public int toolFailureCount() {
        int count = 0;
        for (BenchmarkResult result : results()) {
            for (ToolCallMetric metric : result.runMetrics().toolCalls()) {
                if (!metric.success()) {
                    count++;
                }
            }
        }
        return count;
    }

    @JsonProperty("turnsToSuccess")
    public int turnsToSuccess() {
        int total = 0;
        for (BenchmarkResult result : results()) {
            total += result.turnsToSuccess();
        }
        return total;
    }
}
