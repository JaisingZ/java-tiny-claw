package io.github.tinyclaw.agent.tool.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionPolicySnapshotTest {

    @TempDir
    Path tempDir;

    @Test
    void missingFileReturnsDisabledSnapshot() {
        PermissionPolicySnapshot snapshot = PermissionPolicySnapshot.load(tempDir.resolve(".claw/permissions.yaml"));

        assertThat(snapshot.enabled()).isFalse();
        assertThat(snapshot.evaluate(call("bash", "rm -rf target")).action()).isEqualTo(ToolPermissionAction.ALLOW);
    }

    @Test
    void parsesYamlRulesAndUsesDefaultAction() {
        PermissionPolicySnapshot snapshot = parse("""
                version: 1
                enabled: true
                defaultAction: ask
                approvalTimeoutSeconds: 99

                rules:
                  - id: allow-read
                    tools: [read_file]
                    action: allow

                  - id: deny-dangerous-bash
                    tools: [bash]
                    action: deny
                    arguments:
                      command:
                        regex: '(?i)\\b(rm\\s+-rf|sudo\\b|drop\\s+(database|table)|kubectl\\s+delete)\\b'

                  - id: ask-write-tools
                    tools: [write_file, edit_file, bash]
                    action: ask
                """);

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.approvalTimeout().getSeconds()).isEqualTo(99);
        assertThat(snapshot.ruleCount()).isEqualTo(3);
        assertThat(snapshot.evaluate(call("read_file", "README.md")).action()).isEqualTo(ToolPermissionAction.ALLOW);
        assertThat(snapshot.evaluate(call("bash", "echo hi")).action()).isEqualTo(ToolPermissionAction.ASK);
        assertThat(snapshot.evaluate(call("bash", "kubectl delete pod p1")).action())
                .isEqualTo(ToolPermissionAction.DENY);
        assertThat(snapshot.evaluate(call("unknown", "x")).action()).isEqualTo(ToolPermissionAction.ASK);
    }

    @Test
    void denyWinsOverAskAndAllow() {
        PermissionPolicySnapshot snapshot = parse("""
                enabled: true
                defaultAction: allow
                rules:
                  - id: allow-bash
                    tools: [bash]
                    action: allow
                  - id: ask-bash
                    tools: [bash]
                    action: ask
                  - id: deny-rm
                    tools: [bash]
                    action: deny
                    arguments:
                      command:
                        regex: 'rm\\s+-rf'
                """);

        assertThat(snapshot.evaluate(call("bash", "rm -rf target")).action()).isEqualTo(ToolPermissionAction.DENY);
        assertThat(snapshot.evaluate(call("bash", "echo hi")).action()).isEqualTo(ToolPermissionAction.ASK);
    }

    @Test
    void rejectsDuplicateRuleIds() {
        assertThatThrownBy(() -> parse("""
                enabled: true
                rules:
                  - id: same
                    tools: [bash]
                    action: allow
                  - id: same
                    tools: [read_file]
                    action: allow
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate permission rule id");
    }

    @Test
    void rejectsInvalidRegex() {
        assertThatThrownBy(() -> parse("""
                enabled: true
                rules:
                  - id: bad-regex
                    tools: [bash]
                    action: deny
                    arguments:
                      command:
                        regex: '['
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid regex");
    }

    @Test
    void rejectsInvalidAction() {
        assertThatThrownBy(() -> parse("""
                enabled: true
                rules:
                  - id: bad-action
                    tools: [bash]
                    action: prompt
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action");
    }

    @Test
    void rejectsUnknownFields() {
        assertThatThrownBy(() -> parse("""
                enabled: true
                unknown: value
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown field");
    }

    @Test
    void rejectsUnknownArgumentConditionFields() {
        assertThatThrownBy(() -> parse("""
                enabled: true
                rules:
                  - id: bad-argument
                    tools: [bash]
                    action: deny
                    arguments:
                      command:
                        contains: rm
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown field");
    }

    private static PermissionPolicySnapshot parse(String yaml) {
        return PermissionPolicySnapshot.parse(yaml, Path.of("permissions.yaml"), Instant.now());
    }

    private static ToolCall call(String toolName, String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return new ToolCall(toolName, arguments);
    }
}
