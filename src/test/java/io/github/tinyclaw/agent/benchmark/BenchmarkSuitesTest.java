package io.github.tinyclaw.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkSuitesTest {

    @Test
    void defaultsIncludeConcurrencyCounterRepairCase() {
        List<BenchmarkCase> cases = BenchmarkSuites.defaults();

        BenchmarkCase concurrencyCase = cases.stream()
                .filter(benchmarkCase -> "concurrency_counter_repair".equals(benchmarkCase.id()))
                .findFirst()
                .orElseThrow();

        assertThat(concurrencyCase.name()).contains("concurrency").contains("counter");
        assertThat(concurrencyCase.setupCommand()).contains("CounterRaceCheck.java");
        assertThat(concurrencyCase.taskPrompt()).contains("自行探索").contains("并发安全问题");
        assertThat(concurrencyCase.validateCommand()).contains("CounterRaceCheck").contains("100000");
        assertThat(concurrencyCase.maxSteps()).isGreaterThanOrEqualTo(12);
        assertThat(concurrencyCase.enableThinking()).isTrue();
    }

    @Test
    void windowsEditJsonVersionValidationFailsWhenPatternIsMissing() throws Exception {
        BenchmarkCase editJsonCase = windowsCases().stream()
                .filter(benchmarkCase -> "edit_json_version".equals(benchmarkCase.id()))
                .findFirst()
                .orElseThrow();

        assertThat(editJsonCase.validateCommand())
                .contains("$match = Select-String")
                .contains("if (-not $match) { exit 1 }");
    }

    @Test
    void windowsConcurrencyCounterValidationChecksCompilationBeforeRun() throws Exception {
        BenchmarkCase concurrencyCase = windowsCases().stream()
                .filter(benchmarkCase -> "concurrency_counter_repair".equals(benchmarkCase.id()))
                .findFirst()
                .orElseThrow();

        assertThat(concurrencyCase.validateCommand())
                .contains("javac CounterRaceCheck.java")
                .contains("if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }")
                .contains("java CounterRaceCheck 100000");
    }

    @SuppressWarnings("unchecked")
    private List<BenchmarkCase> windowsCases() throws Exception {
        Method method = BenchmarkSuites.class.getDeclaredMethod("windowsDefaults");
        method.setAccessible(true);
        return (List<BenchmarkCase>) method.invoke(null);
    }
}
