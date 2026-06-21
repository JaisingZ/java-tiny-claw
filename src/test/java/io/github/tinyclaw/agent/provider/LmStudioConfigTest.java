package io.github.tinyclaw.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LmStudioConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsModelAndDefaultBaseUrlFromProperties() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "lmstudio.model=qwen-local\n");

        LmStudioConfig config = LmStudioConfig.load(configPath);

        assertThat(config.baseUrl()).isEqualTo("http://localhost:1234/v1");
        assertThat(config.model()).isEqualTo("qwen-local");
        assertThat(config.thinkingMaxTokens()).isEqualTo(256);
        assertThat(config.actionMaxTokens()).isEqualTo(8192);
    }

    @Test
    void loadsCustomBaseUrlFromExplicitPath() throws Exception {
        Path configPath = tempDir.resolve("custom-agent.properties");
        Files.writeString(configPath, "lmstudio.baseUrl=http://127.0.0.1:2345/v1\n"
                + "lmstudio.model=granite-local\n");

        LmStudioConfig config = LmStudioConfig.load(configPath);

        assertThat(config.baseUrl()).isEqualTo("http://127.0.0.1:2345/v1");
        assertThat(config.model()).isEqualTo("granite-local");
    }

    @Test
    void loadsMaxTokensFromExplicitPath() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "lmstudio.model=qwen-local\n"
                + "lmstudio.thinkingMaxTokens=512\n"
                + "lmstudio.actionMaxTokens=4096\n");

        LmStudioConfig config = LmStudioConfig.load(configPath);

        assertThat(config.thinkingMaxTokens()).isEqualTo(512);
        assertThat(config.actionMaxTokens()).isEqualTo(4096);
    }

    @Test
    void rejectsInvalidMaxTokens() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "lmstudio.model=qwen-local\n"
                + "lmstudio.actionMaxTokens=0\n");

        assertThatThrownBy(() -> LmStudioConfig.load(configPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max tokens must be positive");
    }
}
