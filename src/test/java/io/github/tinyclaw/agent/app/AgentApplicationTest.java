package io.github.tinyclaw.agent.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentApplicationTest {

    @Test
    void noArgsRequiresExplicitCommand() {
        assertThatThrownBy(() -> AgentApplication.resolveStartupMode(new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing command: run or telegram");
    }

    @Test
    void telegramCommandResolvesTelegram() {
        StartupMode mode = AgentApplication.resolveStartupMode(new String[] { "telegram" });

        assertThat(mode).isEqualTo(StartupMode.TELEGRAM);
    }

    @Test
    void runCommandResolvesRunPrompt() {
        StartupMode mode = AgentApplication.resolveStartupMode(new String[] { "run", "--prompt", "hello" });

        assertThat(mode).isEqualTo(StartupMode.RUN_PROMPT);
    }

    @Test
    void startupCheckCommandIsUnknown() {
        assertThatThrownBy(() -> AgentApplication.resolveStartupMode(new String[] { "startup-check" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown command: startup-check");
    }

    @Test
    void telegramCommandRejectsExtraArgs() {
        assertThatThrownBy(() -> AgentApplication.resolveStartupMode(new String[] { "telegram", "--debug" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown telegram option: --debug");
    }

    @Test
    void runOptionsParsesAllSupportedOptions() {
        RunOptions options = RunOptions.parse(
                new String[] { "run", "--plan", "--thinking", "--max-steps", "3", "--debug", "--prompt", "hello" });

        assertThat(options.prompt()).isEqualTo("hello");
        assertThat(options.planMode()).isTrue();
        assertThat(options.thinking()).isTrue();
        assertThat(options.maxSteps()).isEqualTo(3);
        assertThat(options.debug()).isTrue();
    }

    @Test
    void runOptionsKeepsPlanModeDisabledByDefault() {
        RunOptions options = RunOptions.parse(new String[] { "run", "--prompt", "hello" });

        assertThat(options.planMode()).isFalse();
        assertThat(options.maxSteps()).isEqualTo(8);
    }

    @Test
    void runOptionsRequiresPrompt() {
        assertThatThrownBy(() -> RunOptions.parse(new String[] { "run", "--plan" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--prompt is required");
    }

    @Test
    void runOptionsRequiresPromptValue() {
        assertThatThrownBy(() -> RunOptions.parse(new String[] { "run", "--prompt" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing value for --prompt");
    }

    @Test
    void runOptionsRejectsInvalidMaxSteps() {
        assertThatThrownBy(() -> RunOptions.parse(new String[] { "run", "--prompt", "hello", "--max-steps", "0" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("--max-steps must be positive");
        assertThatThrownBy(() -> RunOptions.parse(new String[] { "run", "--prompt", "hello", "--max-steps", "abc" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid --max-steps: abc");
        assertThatThrownBy(() -> RunOptions.parse(new String[] { "run", "--prompt", "hello", "--max-steps" }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing value for --max-steps");
    }

}
