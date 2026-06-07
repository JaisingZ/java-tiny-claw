package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramAgentConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsDefaults() {
        TelegramAgentConfig config = TelegramAgentConfig.from(new HashMap<String, String>());

        assertThat(config.workDir()).isEqualTo(Path.of("."));
        assertThat(config.maxSteps()).isEqualTo(8);
        assertThat(config.enableThinking()).isFalse();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(12);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(12_000);
    }

    @Test
    void loadsFromPropertiesMap() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.workdir", "sandbox");
        values.put("agent.maxSteps", "12");
        values.put("agent.enableThinking", "true");
        values.put("agent.workingMemory.maxMessages", "5");
        values.put("agent.workingMemory.maxChars", "1000");

        TelegramAgentConfig config = TelegramAgentConfig.from(values);

        assertThat(config.workDir()).isEqualTo(Path.of("sandbox"));
        assertThat(config.maxSteps()).isEqualTo(12);
        assertThat(config.enableThinking()).isTrue();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(5);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(1000);
    }

    @Test
    void loadsFromPropertiesFile() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "agent.workdir=work\n"
                + "agent.maxSteps=5\n"
                + "agent.enableThinking=true\n"
                + "agent.workingMemory.maxMessages=6\n"
                + "agent.workingMemory.maxChars=2048\n");

        TelegramAgentConfig config = TelegramAgentConfig.load(configPath);

        assertThat(config.workDir()).isEqualTo(Path.of("work"));
        assertThat(config.maxSteps()).isEqualTo(5);
        assertThat(config.enableThinking()).isTrue();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(6);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(2048);
    }

    @Test
    void rejectsInvalidMaxSteps() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.maxSteps", "0");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.maxSteps");
    }

    @Test
    void rejectsInvalidWorkingMemoryPolicy() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.workingMemory.maxMessages", "0");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.workingMemory.maxMessages");
    }
}
