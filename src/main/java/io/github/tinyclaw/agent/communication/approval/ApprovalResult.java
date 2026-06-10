package io.github.tinyclaw.agent.communication.approval;

/**
 * 人工审批结果。
 */
public final class ApprovalResult {

    private final boolean allowed;
    private final String reason;
    private final String approvalId;

    public ApprovalResult(boolean allowed, String reason, String approvalId) {
        this.allowed = allowed;
        this.reason = reason == null ? "" : reason;
        this.approvalId = approvalId;
    }

    public static ApprovalResult approved(String approvalId) {
        return new ApprovalResult(true, "人类已批准执行", approvalId);
    }

    public static ApprovalResult rejected(String reason, String approvalId) {
        return new ApprovalResult(false, reason, approvalId);
    }

    public boolean allowed() {
        return allowed;
    }

    public String reason() {
        return reason;
    }

    public String approvalId() {
        return approvalId;
    }
}
