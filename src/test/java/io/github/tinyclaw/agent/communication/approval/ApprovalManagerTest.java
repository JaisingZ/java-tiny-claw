package io.github.tinyclaw.agent.communication.approval;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.ToolCall;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ApprovalManagerTest {

    @Test
    void approvesPendingRequestFromSameChat() throws Exception {
        ApprovalManager manager = new ApprovalManager(() -> "approval-1");
        RecordingSession session = new RecordingSession();
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> manager.requestApproval("chat-a", session, bashCall("git push"), Duration.ofSeconds(2)));

        waitForStatus(session, "approval-1");
        boolean handled = manager.resolveCommand("chat-a", "/approve approval-1", session);

        ApprovalResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(result.allowed()).isTrue();
        assertThat(result.approvalId()).isEqualTo("approval-1");
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void rejectsPendingRequestFromSameChat() throws Exception {
        ApprovalManager manager = new ApprovalManager(() -> "approval-2");
        RecordingSession session = new RecordingSession();
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> manager.requestApproval("chat-a", session, bashCall("git push"), Duration.ofSeconds(2)));

        waitForStatus(session, "approval-2");
        boolean handled = manager.resolveCommand("chat-a", "/reject approval-2", session);

        ApprovalResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("拒绝");
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void timesOutAndCleansPendingRequest() {
        ApprovalManager manager = new ApprovalManager(() -> "approval-timeout");
        RecordingSession session = new RecordingSession();

        ApprovalResult result = manager.requestApproval("chat-a", session, bashCall("git push"),
                Duration.ofMillis(30));

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("审批超时");
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void rejectsApprovalFromDifferentChat() throws Exception {
        ApprovalManager manager = new ApprovalManager(() -> "approval-3");
        RecordingSession ownerSession = new RecordingSession();
        RecordingSession otherSession = new RecordingSession();
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> manager.requestApproval("chat-a", ownerSession, bashCall("git push"), Duration.ofSeconds(2)));

        waitForStatus(ownerSession, "approval-3");
        boolean handled = manager.resolveCommand("chat-b", "/approve approval-3", otherSession);
        manager.resolveCommand("chat-a", "/reject approval-3", ownerSession);

        ApprovalResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(otherSession.errors()).containsExactly("只能在发起审批的同一会话处理：approval-3");
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void unknownApprovalCommandIsHandledWithoutStartingAgent() {
        ApprovalManager manager = new ApprovalManager(() -> "approval-4");
        RecordingSession session = new RecordingSession();

        boolean handled = manager.resolveCommand("chat-a", "/approve missing", session);

        assertThat(handled).isTrue();
        assertThat(session.errors()).containsExactly("未找到待审批任务：missing");
    }

    @Test
    void resolveCommandAliasDelegatesToResolve() throws Exception {
        ApprovalManager manager = new ApprovalManager(() -> "approval-6");
        RecordingSession session = new RecordingSession();
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> manager.requestApproval("chat-a", session, bashCall("git push"), Duration.ofSeconds(2)));

        waitForStatus(session, "approval-6");
        boolean handled = manager.resolve("chat-a", "/approve approval-6", session);

        ApprovalResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(result.allowed()).isTrue();
        assertThat(manager.pendingCount()).isZero();
    }

    @Test
    void approveAndRejectByDirectMethods() throws Exception {
        ApprovalManager manager = new ApprovalManager(() -> "approval-7");
        RecordingSession ownerSession = new RecordingSession();
        CompletableFuture<ApprovalResult> future = CompletableFuture.supplyAsync(
                () -> manager.requestApproval("chat-a", ownerSession, bashCall("git push"), Duration.ofSeconds(2)));

        waitForStatus(ownerSession, "approval-7");
        boolean approved = manager.approve("chat-a", "approval-7", ownerSession);
        boolean rejected = manager.reject("chat-a", "approval-7", ownerSession);

        ApprovalResult result = future.get(2, TimeUnit.SECONDS);
        assertThat(approved).isTrue();
        assertThat(rejected).isTrue();
        assertThat(result.allowed()).isTrue();
        assertThat(ownerSession.errors()).containsExactly("未找到待审批任务：approval-7");
        assertThat(ownerSession.statuses().get(ownerSession.statuses().size() - 1)).contains("已批准：approval-7");
    }

    @Test
    void nonApprovalTextIsNotHandled() {
        ApprovalManager manager = new ApprovalManager(() -> "approval-5");

        assertThat(manager.resolveCommand("chat-a", "hello", new RecordingSession())).isFalse();
    }

    private static void waitForStatus(RecordingSession session, String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (session.statuses().stream().anyMatch(value -> value.contains(text))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for status: " + text);
    }

    private static ToolCall bashCall(String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return new ToolCall("bash", arguments);
    }

    private static final class RecordingSession implements ChatSession {
        private final List<String> statuses = Collections.synchronizedList(new ArrayList<String>());
        private final List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public void sendText(String text) {
            statuses.add(text);
        }

        @Override
        public void sendStatus(String text) {
            statuses.add(text);
        }

        @Override
        public void sendError(String text) {
            errors.add(text);
        }

        private List<String> statuses() {
            return statuses;
        }

        private List<String> errors() {
            return errors;
        }
    }
}
