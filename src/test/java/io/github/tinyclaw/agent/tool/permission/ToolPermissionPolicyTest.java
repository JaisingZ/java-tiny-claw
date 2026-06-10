package io.github.tinyclaw.agent.tool.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolPermissionPolicyTest {

    @Test
    void defaultPolicyUsesSafeDefaults() {
        ToolPermissionConfig config = ToolPermissionConfig.from(null);
        ToolPermissionPolicy policy = new ToolPermissionPolicy(config);

        assertThat(config.enabled()).isFalse();
        assertThat(config.approvalTimeout().getSeconds()).isEqualTo(1800);
        assertThat(config.toolActions().get("read_file")).isEqualTo(ToolPermissionAction.ALLOW);
        assertThat(config.toolActions().get("write_file")).isEqualTo(ToolPermissionAction.ASK);
        assertThat(config.toolActions().get("edit_file")).isEqualTo(ToolPermissionAction.ASK);
        assertThat(config.toolActions().get("bash")).isEqualTo(ToolPermissionAction.ASK);
        assertThat(policy.evaluate(call("bash", "rm -rf /tmp/a")).action()).isEqualTo(ToolPermissionAction.ALLOW);
        assertThat(policy.evaluate(call("custom_tool", "x")).action()).isEqualTo(ToolPermissionAction.ALLOW);
    }

    @Test
    void disabledPolicyAllowsAllTools() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.tool.bash", "deny");

        ToolPermissionDecision decision = ToolPermissionPolicy.from(values).evaluate(call("bash", "rm -rf /tmp/a"));

        assertThat(decision.action()).isEqualTo(ToolPermissionAction.ALLOW);
    }

    @Test
    void usesExactToolActionWhenEnabled() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.tool.read_file", "allow");
        values.put("agent.permissions.tool.write_file", "ask");
        values.put("agent.permissions.tool.edit_file", "deny");

        ToolPermissionPolicy policy = ToolPermissionPolicy.from(values);

        assertThat(policy.evaluate(call("read_file", "README.md")).action()).isEqualTo(ToolPermissionAction.ALLOW);
        assertThat(policy.evaluate(call("write_file", "README.md")).action()).isEqualTo(ToolPermissionAction.ASK);
        assertThat(policy.evaluate(call("edit_file", "README.md")).action()).isEqualTo(ToolPermissionAction.DENY);
    }

    @Test
    void denyPatternWinsOverToolAllow() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.tool.bash", "allow");
        values.put("agent.permissions.denyPattern.1", "(?i)rm\\s+-rf");

        ToolPermissionDecision decision = ToolPermissionPolicy.from(values).evaluate(call("bash", "rm -rf target"));

        assertThat(decision.action()).isEqualTo(ToolPermissionAction.DENY);
        assertThat(decision.reason()).contains("rm\\s+-rf");
    }

    @Test
    void unknownToolsAskWhenEnabled() {
        ToolPermissionDecision decision = ToolPermissionPolicy.from(enabled()).evaluate(call("custom_tool", "x"));

        assertThat(decision.action()).isEqualTo(ToolPermissionAction.ASK);
    }

    @Test
    void denyPatternOrderMatchesOrderedIndex() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.tool.bash", "allow");
        values.put("agent.permissions.denyPattern.10", "allowme");
        values.put("agent.permissions.denyPattern.2", "(?i)rm");
        values.put("agent.permissions.denyPattern.xyz", "ignore");

        ToolPermissionPolicy policy = ToolPermissionPolicy.from(values);

        ToolPermissionDecision decision = policy.evaluate(call("bash", "rm -rf allowme"));
        assertThat(decision.action()).isEqualTo(ToolPermissionAction.DENY);
        assertThat(decision.reason()).contains("(?i)rm");
    }

    @Test
    void parsesApprovalTimeout() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.approvalTimeoutSeconds", "7");

        ToolPermissionConfig config = ToolPermissionConfig.from(values);

        assertThat(config.approvalTimeout().getSeconds()).isEqualTo(7);
    }

    @Test
    void rejectsInvalidAction() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.tool.bash", "maybe");

        assertThatThrownBy(() -> ToolPermissionConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.tool.bash");
    }

    @Test
    void rejectsInvalidTimeout() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.approvalTimeoutSeconds", "0");

        assertThatThrownBy(() -> ToolPermissionConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.approvalTimeoutSeconds");
    }

    @Test
    void rejectsInvalidRegex() {
        Map<String, String> values = enabled();
        values.put("agent.permissions.denyPattern.1", "[");

        assertThatThrownBy(() -> ToolPermissionConfig.from(values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.permissions.denyPattern.1");
    }

    private static Map<String, String> enabled() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.enabled", "true");
        return values;
    }

    private static ToolCall call(String toolName, String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return new ToolCall(toolName, arguments);
    }
}
