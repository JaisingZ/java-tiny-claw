package io.github.tinyclaw.agent.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.runtime.ConsoleRunLogger;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.RunStatus;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AgentApplicationTest {

    @Test
    void debugRunOutputPrintsOnlyCompactResult() {
        RunResult result = RunResult.success(1, Collections.singletonList("Hello，Java！"), "done");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleRunLogger logger = new ConsoleRunLogger(
                new PrintStream(output, true, StandardCharsets.UTF_8), false);

        AgentApplication.emitRunOutput(logger, result, true);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("RESULT status=SUCCESS answer=done failure=null")
                .doesNotContain("TRACE")
                .doesNotContain("OBSERVATIONS")
                .doesNotContain("Hello，Java！");
    }

    @Test
    void normalRunOutputKeepsResultAndObservations() {
        RunResult result = new RunResult(RunStatus.SUCCESS, "done", null, 1,
                Collections.singletonList("Hello，Java！"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleRunLogger logger = new ConsoleRunLogger(
                new PrintStream(output, true, StandardCharsets.UTF_8), false);

        AgentApplication.emitRunOutput(logger, result, false);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("RESULT status=SUCCESS answer=done failure=null")
                .contains("OBSERVATIONS")
                .contains("Hello，Java！")
                .doesNotContain("TRACE");
    }
}
