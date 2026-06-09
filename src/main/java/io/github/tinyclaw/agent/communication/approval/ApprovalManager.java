package io.github.tinyclaw.agent.communication.approval;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.ToolCall;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 进程内人工审批管理器。
 */
public final class ApprovalManager {

    private final Map<String, PendingApproval> pendingApprovals = new ConcurrentHashMap<String, PendingApproval>();
    private final Supplier<String> idSupplier;

    public ApprovalManager() {
        this(() -> "approval-" + UUID.randomUUID());
    }

    public ApprovalManager(Supplier<String> idSupplier) {
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
    }

    public ApprovalResult requestApproval(String chatId, ChatSession session, ToolCall call, Duration timeout) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(timeout, "timeout");
        String approvalId = nextApprovalId();
        CompletableFuture<ApprovalResult> future = new CompletableFuture<ApprovalResult>();
        pendingApprovals.put(approvalId, new PendingApproval(chatId, future));
        session.sendStatus(approvalMessage(approvalId, call));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            return ApprovalResult.rejected("审批超时，已自动拒绝: " + approvalId, approvalId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ApprovalResult.rejected("审批等待被中断: " + approvalId, approvalId);
        } catch (Exception ex) {
            return ApprovalResult.rejected("审批失败: " + ex.getMessage(), approvalId);
        } finally {
            pendingApprovals.remove(approvalId);
        }
    }

    public boolean resolve(String chatId, String text, ChatSession session) {
        return resolveCommand(chatId, text, session);
    }

    public boolean resolve(String chatId, boolean approved, String approvalId, ChatSession session) {
        Objects.requireNonNull(session, "session");
        if (!hasText(approvalId)) {
            return false;
        }

        PendingApproval pending = pendingApprovals.get(approvalId);
        if (pending == null) {
            session.sendError("未找到待审批任务：" + approvalId);
            return true;
        }
        if (!sameChat(chatId, pending.chatId())) {
            session.sendError("只能在发起审批的同一会话处理：" + approvalId);
            return true;
        }

        pendingApprovals.remove(approvalId);
        ApprovalResult result = approved
                ? ApprovalResult.approved(approvalId)
                : ApprovalResult.rejected("人类已拒绝执行", approvalId);
        pending.future().complete(result);
        session.sendStatus((approved ? "已批准：" : "已拒绝：") + approvalId);
        return true;
    }

    public boolean approve(String chatId, String approvalId, ChatSession session) {
        return resolve(chatId, true, approvalId, session);
    }

    public boolean reject(String chatId, String approvalId, ChatSession session) {
        return resolve(chatId, false, approvalId, session);
    }

    public boolean resolveCommand(String chatId, String text, ChatSession session) {
        ApprovalCommand command = ApprovalCommand.parse(text);
        if (command == null) {
            return false;
        }
        return resolve(chatId, command.approved(), command.approvalId(), session);
    }

    int pendingCount() {
        return pendingApprovals.size();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nextApprovalId() {
        String approvalId = idSupplier.get();
        while (pendingApprovals.containsKey(approvalId)) {
            approvalId = idSupplier.get();
        }
        return approvalId;
    }

    private String approvalMessage(String approvalId, ToolCall call) {
        return "需要人工审批\n"
                + "ID: " + approvalId + "\n"
                + "工具: " + call.toolName() + "\n"
                + "参数: " + call.arguments() + "\n"
                + "回复 /approve " + approvalId + " 或 /reject " + approvalId;
    }

    private boolean sameChat(String actual, String expected) {
        return Objects.equals(normalizeChatId(actual), normalizeChatId(expected));
    }

    private String normalizeChatId(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class PendingApproval {
        private final String chatId;
        private final CompletableFuture<ApprovalResult> future;

        private PendingApproval(String chatId, CompletableFuture<ApprovalResult> future) {
            this.chatId = chatId;
            this.future = future;
        }

        private String chatId() {
            return chatId;
        }

        private CompletableFuture<ApprovalResult> future() {
            return future;
        }
    }

    private static final class ApprovalCommand {
        private final boolean approved;
        private final String approvalId;

        private ApprovalCommand(boolean approved, String approvalId) {
            this.approved = approved;
            this.approvalId = approvalId;
        }

        private static ApprovalCommand parse(String text) {
            if (text == null) {
                return null;
            }
            String trimmed = text.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("/approve ")) {
                return new ApprovalCommand(true, trimmed.substring("/approve ".length()).trim());
            }
            if (lower.startsWith("approve ")) {
                return new ApprovalCommand(true, trimmed.substring("approve ".length()).trim());
            }
            if (lower.startsWith("/reject ")) {
                return new ApprovalCommand(false, trimmed.substring("/reject ".length()).trim());
            }
            if (lower.startsWith("reject ")) {
                return new ApprovalCommand(false, trimmed.substring("reject ".length()).trim());
            }
            return null;
        }

        private boolean approved() {
            return approved;
        }

        private String approvalId() {
            return approvalId;
        }
    }
}
