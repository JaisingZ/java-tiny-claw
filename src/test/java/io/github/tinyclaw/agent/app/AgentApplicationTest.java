package io.github.tinyclaw.agent.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.RunStatus;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        RecordingRunLogger logger = new RecordingRunLogger();

        AgentApplication.emitRunOutput(logger, result, true);

        assertThat(logger.lines()).containsExactly("RESULT status=SUCCESS answer=done failure=null");
    }

    @Test
    void normalRunOutputKeepsResultAndObservations() {
        RunResult result = new RunResult(RunStatus.SUCCESS, "done", null, 1,
                Collections.singletonList("Hello，Java！"));
        RecordingRunLogger logger = new RecordingRunLogger();

        AgentApplication.emitRunOutput(logger, result, false);

        assertThat(logger.lines()).containsExactly(
                "RESULT status=SUCCESS answer=done failure=null",
                "OBSERVATIONS",
                "Hello，Java！");
    }

    private static final class RecordingRunLogger implements RunLogger {

        private final List<String> lines = new ArrayList<String>();

        @Override
        public void writeLine(String line) {
            lines.add(line);
        }

        @Override
        public void writeBlankLine() {
            lines.add("");
        }

        @Override
        public void registryMounted(String toolName) {
        }

        @Override
        public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
                List<ToolDefinition> tools) {
        }

        @Override
        public void turnStarted(int turn) {
        }

        @Override
        public void thinkingStarted() {
        }

        @Override
        public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        }

        @Override
        public void actionStarted(List<ToolDefinition> tools) {
        }

        @Override
        public void toolDecision(ToolDecision decision) {
        }

        @Override
        public void toolStarted(ToolCall call) {
        }

        @Override
        public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        }

        @Override
        public void finished(FinishDecision decision) {
        }

        @Override
        public void failed(String reason) {
        }

        private List<String> lines() {
            return lines;
        }
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
