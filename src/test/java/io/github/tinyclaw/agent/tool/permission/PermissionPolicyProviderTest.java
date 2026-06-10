package io.github.tinyclaw.agent.tool.permission;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PermissionPolicyProviderTest {

    @Test
    void reloadSwapsSnapshotWhenValid() {
        PermissionPolicySnapshot allowSnapshot = parse("""
                enabled: true
                defaultAction: allow
                """);
        PermissionPolicySnapshot denySnapshot = parse("""
                enabled: true
                defaultAction: deny
                """);
        PermissionPolicyProvider provider = new PermissionPolicyProvider(Path.of("permissions.yaml"),
                allowSnapshot, () -> denySnapshot);

        assertThat(provider.reload()).isTrue();

        assertThat(provider.current().evaluate(call("bash", "echo hi")).action()).isEqualTo(ToolPermissionAction.DENY);
    }

    @Test
    void invalidReloadKeepsLastKnownGoodSnapshot() {
        PermissionPolicySnapshot allowSnapshot = parse("""
                enabled: true
                defaultAction: allow
                """);
        PermissionPolicyProvider provider = new PermissionPolicyProvider(Path.of("permissions.yaml"),
                allowSnapshot, () -> {
                    throw new IllegalStateException("bad yaml");
                });

        assertThat(provider.reload()).isFalse();

        assertThat(provider.current().evaluate(call("bash", "echo hi")).action()).isEqualTo(ToolPermissionAction.ALLOW);
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
