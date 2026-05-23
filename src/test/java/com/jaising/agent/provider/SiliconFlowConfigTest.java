package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiliconFlowConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsApiKeyModelAndDefaultBaseUrlFromProperties() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "siliconflow.apiKey=test-key\n"
                + "siliconflow.model=Qwen/Qwen3-8B\n");

        SiliconFlowConfig config = SiliconFlowConfig.load(configPath);

        assertThat(config.apiKey()).isEqualTo("test-key");
        assertThat(config.baseUrl()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(config.model()).isEqualTo("Qwen/Qwen3-8B");
    }

    @Test
    void loadDefaultUsesAgentConfigSystemProperty() throws Exception {
        Path configPath = tempDir.resolve("custom-agent.properties");
        Files.writeString(configPath, "siliconflow.apiKey=custom-key\n"
                + "siliconflow.baseUrl=http://localhost:8080/v1\n"
                + "siliconflow.model=custom-model\n");
        String previous = System.getProperty("agent.config");
        System.setProperty("agent.config", configPath.toString());
        try {
            SiliconFlowConfig config = SiliconFlowConfig.loadDefault();

            assertThat(config.apiKey()).isEqualTo("custom-key");
            assertThat(config.baseUrl()).isEqualTo("http://localhost:8080/v1");
            assertThat(config.model()).isEqualTo("custom-model");
        } finally {
            if (previous == null) {
                System.clearProperty("agent.config");
            } else {
                System.setProperty("agent.config", previous);
            }
        }
    }
}
