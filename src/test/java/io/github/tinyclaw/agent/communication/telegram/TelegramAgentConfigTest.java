package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.tool.permission.ToolPermissionAction;
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
        assertThat(config.planMode()).isFalse();
        assertThat(config.debug()).isFalse();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(12);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(12_000);
        assertThat(config.toolPermissionConfig().enabled()).isFalse();
        assertThat(config.toolPermissionConfig().approvalTimeout().getSeconds()).isEqualTo(1_800);
        assertThat(config.toolPermissionConfig().toolActions().get("read_file")).isEqualTo(ToolPermissionAction.ALLOW);
        assertThat(config.toolPermissionConfig().toolActions().get("bash")).isEqualTo(ToolPermissionAction.ASK);
        assertThat(config.toolPermissionConfig().permissionFile()).isEqualTo(Path.of(".claw/permissions.yaml"));
        assertThat(config.toolPermissionConfig().hotReload()).isTrue();
        assertThat(config.toolPermissionConfig().reloadInterval().getSeconds()).isEqualTo(2);
    }

    @Test
    void loadsFromPropertiesMap() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.workdir", "sandbox");
        values.put("agent.maxSteps", "12");
        values.put("agent.enableThinking", "true");
        values.put("agent.planMode", "true");
        values.put("agent.debug", "true");
        values.put("agent.workingMemory.maxMessages", "5");
        values.put("agent.workingMemory.maxChars", "1000");
        values.put("agent.permissions.enabled", "true");
        values.put("agent.permissions.approvalTimeoutSeconds", "9");
        values.put("agent.permissions.file", "config/permissions.yaml");
        values.put("agent.permissions.hotReload", "false");
        values.put("agent.permissions.reloadIntervalSeconds", "3");
        values.put("agent.permissions.tool.bash", "deny");
        values.put("agent.permissions.denyPattern.1", "(?i)rm\\s+-rf");

        TelegramAgentConfig config = TelegramAgentConfig.from(values);

        assertThat(config.workDir()).isEqualTo(Path.of("sandbox"));
        assertThat(config.maxSteps()).isEqualTo(12);
        assertThat(config.enableThinking()).isTrue();
        assertThat(config.planMode()).isTrue();
        assertThat(config.debug()).isTrue();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(5);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(1000);
        assertThat(config.toolPermissionConfig().enabled()).isTrue();
        assertThat(config.toolPermissionConfig().approvalTimeout().getSeconds()).isEqualTo(9);
        assertThat(config.toolPermissionConfig().permissionFile()).isEqualTo(Path.of("config/permissions.yaml"));
        assertThat(config.toolPermissionConfig().hotReload()).isFalse();
        assertThat(config.toolPermissionConfig().reloadInterval().getSeconds()).isEqualTo(3);
        assertThat(config.toolPermissionConfig().toolActions().get("bash")).isEqualTo(ToolPermissionAction.DENY);
        assertThat(config.toolPermissionConfig().denyPatterns()).hasSize(1);
    }

    @Test
    void loadsFromPropertiesFile() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "agent.workdir=work\n"
                + "agent.maxSteps=5\n"
                + "agent.enableThinking=true\n"
                + "agent.planMode=true\n"
                + "agent.debug=true\n"
                + "agent.workingMemory.maxMessages=6\n"
                + "agent.workingMemory.maxChars=2048\n"
                + "agent.permissions.enabled=true\n"
                + "agent.permissions.approvalTimeoutSeconds=11\n"
                + "agent.permissions.file=config/permissions.yaml\n"
                + "agent.permissions.hotReload=false\n"
                + "agent.permissions.reloadIntervalSeconds=4\n"
                + "agent.permissions.tool.write_file=deny\n");

        TelegramAgentConfig config = TelegramAgentConfig.load(configPath);

        assertThat(config.workDir()).isEqualTo(Path.of("work"));
        assertThat(config.maxSteps()).isEqualTo(5);
        assertThat(config.enableThinking()).isTrue();
        assertThat(config.planMode()).isTrue();
        assertThat(config.debug()).isTrue();
        assertThat(config.workingMemoryPolicy().maxMessages()).isEqualTo(6);
        assertThat(config.workingMemoryPolicy().maxChars()).isEqualTo(2048);
        assertThat(config.toolPermissionConfig().enabled()).isTrue();
        assertThat(config.toolPermissionConfig().approvalTimeout().getSeconds()).isEqualTo(11);
        assertThat(config.toolPermissionConfig().permissionFile()).isEqualTo(Path.of("config/permissions.yaml"));
        assertThat(config.toolPermissionConfig().hotReload()).isFalse();
        assertThat(config.toolPermissionConfig().reloadInterval().getSeconds()).isEqualTo(4);
        assertThat(config.toolPermissionConfig().toolActions().get("write_file")).isEqualTo(ToolPermissionAction.DENY);
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

    @Test
    void rejectsInvalidPlanMode() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.planMode", "yes");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.planMode");
    }

    @Test
    void rejectsInvalidDebug() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.debug", "yes");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.debug");
    }

    @Test
    void rejectsInvalidPermissionAction() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.tool.bash", "prompt");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.tool.bash");
    }

    @Test
    void rejectsInvalidApprovalTimeout() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.approvalTimeoutSeconds", "-1");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.approvalTimeoutSeconds");
    }

    @Test
    void rejectsInvalidHotReload() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.hotReload", "yes");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.hotReload");
    }

    @Test
    void rejectsInvalidReloadInterval() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.reloadIntervalSeconds", "0");

        assertThatThrownBy(() -> TelegramAgentConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.reloadIntervalSeconds");
    }
}
