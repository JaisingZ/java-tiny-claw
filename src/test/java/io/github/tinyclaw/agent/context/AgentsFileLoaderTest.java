package io.github.tinyclaw.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentsFileLoaderTest {

    @TempDir
    Path workDir;

    @Test
    void loadsStandardAgentsFile() throws Exception {
        Files.writeString(workDir.resolve("AGENTS.md"), "- 始终使用简体中文沟通", StandardCharsets.UTF_8);

        String content = new AgentsFileLoader(workDir).load();

        assertThat(content).isEqualTo("- 始终使用简体中文沟通");
    }

    @Test
    void returnsEmptyWhenAgentsFileIsMissing() {
        String content = new AgentsFileLoader(workDir).load();

        assertThat(content).isEmpty();
    }

    @Test
    void ignoresLegacyAgentFile() throws Exception {
        Files.writeString(workDir.resolve("AGENT.md"), "- legacy rule", StandardCharsets.UTF_8);

        String content = new AgentsFileLoader(workDir).load();

        assertThat(content).isEmpty();
    }
}
