package io.github.tinyclaw.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.provider.ModelUsage;
import io.github.tinyclaw.agent.runtime.AgentSession;
import io.github.tinyclaw.agent.runtime.ModelCallMetric;
import io.github.tinyclaw.agent.runtime.RunMetrics;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.ToolCallMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void setupFailureDoesNotRunAgent() throws Exception {
        RecordingAgentRunner agentRunner = new RecordingAgentRunner(successResult());
        ScriptedCommandExecutor commands = new ScriptedCommandExecutor(
                CommandResult.failed(2, "setup exploded"));
        BenchmarkRunner runner = new BenchmarkRunner(tempDir, agentRunner, commands);

        BenchmarkReport report = runner.runSuite(Collections.singletonList(caseWithId("setup_fail")));

        BenchmarkResult result = report.results().get(0);
        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).contains("setup failed").contains("setup exploded");
        assertThat(agentRunner.workDirs).isEmpty();
        assertThat(commands.commands).containsExactly("setup");
    }

    @Test
    void validateFailureKeepsValidationOutputAndMetrics() throws Exception {
        RecordingAgentRunner agentRunner = new RecordingAgentRunner(successResult());
        ScriptedCommandExecutor commands = new ScriptedCommandExecutor(
                CommandResult.success("setup ok"),
                CommandResult.failed(1, "assertion failed"));
        BenchmarkRunner runner = new BenchmarkRunner(tempDir, agentRunner, commands);

        BenchmarkReport report = runner.runSuite(Collections.singletonList(caseWithId("validate_fail")));

        BenchmarkResult result = report.results().get(0);
        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).contains("validation failed");
        assertThat(result.validationOutput()).isEqualTo("assertion failed");
        assertThat(result.runMetrics().modelCallCount()).isEqualTo(1);
        assertThat(result.turnsToSuccess()).isEqualTo(1);
        assertThat(report.passedCases()).isZero();
    }

    @Test
    void validateSuccessUsesIsolatedWorkspaceAndReportsMetrics() throws Exception {
        RecordingAgentRunner agentRunner = new RecordingAgentRunner(successResult(), successResult());
        ScriptedCommandExecutor commands = new ScriptedCommandExecutor(
                CommandResult.success("setup one"),
                CommandResult.success("validate one"),
                CommandResult.success("setup two"),
                CommandResult.success("validate two"));
        BenchmarkRunner runner = new BenchmarkRunner(tempDir, agentRunner, commands);

        BenchmarkReport report = runner.runSuite(Arrays.asList(caseWithId("case_one"), caseWithId("case_two")));

        assertThat(report.results()).hasSize(2);
        assertThat(report.passedCases()).isEqualTo(2);
        assertThat(report.modelCalls()).isEqualTo(2);
        assertThat(report.promptTokens()).isEqualTo(20);
        assertThat(report.toolFailureCount()).isEqualTo(2);
        assertThat(agentRunner.workDirs).hasSize(2);
        assertThat(agentRunner.workDirs.get(0)).isNotEqualTo(agentRunner.workDirs.get(1));
        assertThat(agentRunner.workDirs.get(0)).startsWith(tempDir.resolve("workspaces"));
        assertThat(Files.exists(agentRunner.workDirs.get(0))).isTrue();
        assertThat(report.results().get(0).traceDir()).contains(".tinyclaw");
    }

    private BenchmarkCase caseWithId(String id) {
        return new BenchmarkCase(id, "case " + id, "setup", "do task", "validate", 3, false);
    }

    private RunResult successResult() {
        RunMetrics metrics = new RunMetrics(
                Collections.singletonList(new ModelCallMetric(DecisionPhase.ACTION, "model-a", 20L, true, null,
                        new ModelUsage(10, 3, 13), true)),
                Arrays.asList(
                        new ToolCallMetric("bash", 7L, true, 12, null),
                        new ToolCallMetric("bash", 5L, false, 0, "exitCode=1")));
        return RunResult.success(1, Collections.singletonList("ok"), "done", metrics);
    }

    private static final class RecordingAgentRunner implements BenchmarkRunner.AgentRunner {
        private final List<RunResult> results;
        private final List<Path> workDirs = new ArrayList<Path>();
        private int index;

        private RecordingAgentRunner(RunResult... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public RunResult run(Path workDir, BenchmarkCase benchmarkCase, AgentSession session) {
            workDirs.add(workDir);
            session.record(results.get(index).metrics());
            return results.get(index++);
        }
    }

    private static final class ScriptedCommandExecutor implements BenchmarkRunner.CommandExecutor {
        private final List<CommandResult> results;
        private final List<String> commands = new ArrayList<String>();
        private int index;

        private ScriptedCommandExecutor(CommandResult... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public CommandResult execute(Path workDir, String command) {
            commands.add(command);
            return results.get(index++);
        }
    }
}
