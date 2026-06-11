package io.github.tinyclaw.agent.benchmark;

import io.github.tinyclaw.agent.runtime.RunMetrics;

/**
 * 单条 benchmark case 的结果快照。
 */
public record BenchmarkResult(
        String caseId,
        String name,
        boolean passed,
        String errorMessage,
        long durationMillis,
        RunMetrics runMetrics,
        String traceDir,
        String validationOutput,
        int turnsToSuccess) {

    public BenchmarkResult {
        caseId = caseId == null ? "" : caseId;
        name = name == null ? "" : name;
        errorMessage = errorMessage == null ? "" : errorMessage;
        runMetrics = runMetrics == null ? RunMetrics.empty() : runMetrics;
        traceDir = traceDir == null ? "" : traceDir;
        validationOutput = validationOutput == null ? "" : validationOutput;
        durationMillis = Math.max(0L, durationMillis);
        turnsToSuccess = Math.max(0, turnsToSuccess);
    }
}
