package io.github.tinyclaw.agent.communication.approval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import io.github.tinyclaw.agent.tool.permission.PermissionPolicyProvider;
import io.github.tinyclaw.agent.tool.permission.PermissionPolicySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolApprovalMiddlewareTest {

    @Test
    void readsLatestPolicySnapshotOnEachExecution(@TempDir Path tempDir) throws Exception {
        PermissionPolicySnapshot allowSnapshot = load(tempDir, "allow.yaml", """
                enabled: true
                defaultAction: allow
                """);
        PermissionPolicySnapshot denySnapshot = load(tempDir, "deny.yaml", """
                enabled: true
                defaultAction: deny
                """);
        AtomicReference<PermissionPolicySnapshot> reloadedSnapshot =
                new AtomicReference<PermissionPolicySnapshot>(denySnapshot);
        PermissionPolicyProvider provider = new PermissionPolicyProvider(Path.of("permissions.yaml"),
                allowSnapshot, reloadedSnapshot::get);
        ToolApprovalMiddleware middleware = new ToolApprovalMiddleware(provider, new ApprovalManager(),
                "chat-a", new RecordingSession());
        AtomicInteger executed = new AtomicInteger();

        ToolResult first = middleware.execute(call("bash", "echo hi"), context(), (call, context) -> {
            executed.incrementAndGet();
            return ToolResult.success("ok");
        });
        provider.reload();
        ToolResult second = middleware.execute(call("bash", "echo hi"), context(), (call, context) -> {
            executed.incrementAndGet();
            return ToolResult.success("ok");
        });

        assertThat(first.success()).isTrue();
        assertThat(second.success()).isFalse();
        assertThat(second.errorMessage()).contains("permission_denied");
        assertThat(executed.get()).isEqualTo(1);
    }

    private static PermissionPolicySnapshot load(Path tempDir, String name, String yaml) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, yaml);
        return PermissionPolicySnapshot.load(path);
    }

    private static AgentContext context() {
        return AgentContext.create(new Task("t1", "run"));
    }

    private static ToolCall call(String toolName, String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return new ToolCall(toolName, arguments);
    }

    private static final class RecordingSession implements ChatSession {

        @Override
        public void sendText(String text) {
        }

        @Override
        public void sendStatus(String text) {
        }

        @Override
        public void sendError(String text) {
        }
    }
}
