package io.github.tinyclaw.agent.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void noArgsKeepsHarnessWhenTelegramStartupDisabled() throws Exception {
        FakeRuntime runtime = new FakeRuntime(false);

        AgentApplication.execute(new String[0], runtime);

        assertThat(runtime.harnessStarts).isEqualTo(1);
        assertThat(runtime.telegramStarts).isZero();
        assertThat(runtime.awaitCalls).isZero();
    }

    @Test
    void noArgsStartsTelegramServiceWhenEnabledByConfig() throws Exception {
        FakeRuntime runtime = new FakeRuntime(true);

        AgentApplication.execute(new String[0], runtime);

        assertThat(runtime.harnessStarts).isZero();
        assertThat(runtime.telegramStarts).isEqualTo(1);
        assertThat(runtime.awaitCalls).isEqualTo(1);
        assertThat(runtime.telegramStops).isEqualTo(1);
        assertThat(runtime.shutdownHooks).isEqualTo(1);
    }

    @Test
    void telegramCommandStartsTelegramServiceEvenWhenConfigDisabled() throws Exception {
        FakeRuntime runtime = new FakeRuntime(false);

        AgentApplication.execute(new String[] { "telegram" }, runtime);

        assertThat(runtime.harnessStarts).isZero();
        assertThat(runtime.telegramStarts).isEqualTo(1);
        assertThat(runtime.awaitCalls).isEqualTo(1);
        assertThat(runtime.runCalls).isZero();
    }

    @Test
    void runCommandDoesNotStartTelegramService() throws Exception {
        FakeRuntime runtime = new FakeRuntime(true);

        AgentApplication.execute(new String[] { "run", "--prompt", "hello" }, runtime);

        assertThat(runtime.runCalls).isEqualTo(1);
        assertThat(runtime.telegramStarts).isZero();
        assertThat(runtime.harnessStarts).isZero();
    }

    @Test
    void startupCheckCommandIsUnknown() {
        FakeRuntime runtime = new FakeRuntime(true);

        assertThatThrownBy(() -> AgentApplication.execute(new String[] { "startup-check" }, runtime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown command: startup-check");

        assertThat(runtime.telegramStarts).isZero();
        assertThat(runtime.harnessStarts).isZero();
        assertThat(runtime.runCalls).isZero();
    }

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

    private static final class FakeRuntime implements AgentApplication.ApplicationRuntime {

        private final boolean telegramEnabled;
        private int harnessStarts;
        private int telegramStarts;
        private int telegramStops;
        private int awaitCalls;
        private int shutdownHooks;
        private int runCalls;

        private FakeRuntime(boolean telegramEnabled) {
            this.telegramEnabled = telegramEnabled;
        }

        @Override
        public boolean telegramWebhookEnabled() {
            return telegramEnabled;
        }

        @Override
        public void startHarness() {
            harnessStarts++;
        }

        @Override
        public void runPrompt(String[] args) {
            runCalls++;
        }

        @Override
        public AgentApplication.ManagedService createTelegramService() {
            return new AgentApplication.ManagedService() {
                @Override
                public void start() {
                    telegramStarts++;
                }

                @Override
                public void stop() {
                    telegramStops++;
                }
            };
        }

        @Override
        public void addShutdownHook(Runnable hook) {
            shutdownHooks++;
        }

        @Override
        public void awaitStop() {
            awaitCalls++;
        }
    }
}
