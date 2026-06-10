package io.github.tinyclaw.agent.tool.permission;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionFileWatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void hotReloadsValidYamlAndKeepsOldSnapshotAfterInvalidYaml() throws Exception {
        Path policyFile = tempDir.resolve(".claw").resolve("permissions.yaml");
        Files.createDirectories(policyFile.getParent());
        Files.writeString(policyFile, """
                enabled: true
                defaultAction: allow
                """);
        PermissionPolicyProvider provider = PermissionPolicyProvider.load(policyFile);

        try (PermissionFileWatcher watcher = new PermissionFileWatcher(provider, Duration.ofMillis(50))) {
            watcher.start();
            Files.writeString(policyFile, """
                    enabled: true
                    defaultAction: allow
                    rules:
                      - id: deny-kubectl-delete
                        tools: [bash]
                        action: deny
                        arguments:
                          command:
                            regex: 'kubectl\\s+delete'
                    """);

            waitUntil(() -> provider.current().evaluate(call("bash", "kubectl delete pod p1")).isDeny());
            assertThat(provider.current().evaluate(call("bash", "kubectl delete pod p1")).action())
                    .isEqualTo(ToolPermissionAction.DENY);

            Files.writeString(policyFile, "enabled: true\nrules: [\n");
            Thread.sleep(500L);

            assertThat(provider.current().evaluate(call("bash", "kubectl delete pod p1")).action())
                    .isEqualTo(ToolPermissionAction.DENY);
        }
    }

    private static void waitUntil(Check check) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.passed()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("condition did not pass before timeout");
    }

    private static ToolCall call(String toolName, String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return new ToolCall(toolName, arguments);
    }

    @FunctionalInterface
    private interface Check {

        boolean passed();
    }
}
