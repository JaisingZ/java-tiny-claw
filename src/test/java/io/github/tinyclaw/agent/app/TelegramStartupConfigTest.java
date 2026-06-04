package io.github.tinyclaw.agent.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramStartupConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsToDisabled() {
        TelegramStartupConfig config = TelegramStartupConfig.from(new HashMap<String, String>());

        assertThat(config.enabled()).isFalse();
    }

    @Test
    void readsEnabledFromPropertyName() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("telegram.webhook.enabled", "true");

        TelegramStartupConfig config = TelegramStartupConfig.from(values);

        assertThat(config.enabled()).isTrue();
    }

    @Test
    void rejectsInvalidBoolean() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("telegram.webhook.enabled", "maybe");

        assertThatThrownBy(() -> TelegramStartupConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("telegram.webhook.enabled");
    }

    @Test
    void loadsFromExplicitPath() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "telegram.webhook.enabled=true\n");

        TelegramStartupConfig config = TelegramStartupConfig.load(configPath);

        assertThat(config.enabled()).isTrue();
    }
}
