package io.github.tinyclaw.agent.domain;

import java.util.Objects;

/**
 * thinking 后的自检决策。
 */
public final class ReviewDecision implements Decision {

    public enum Status {
        APPROVED,
        REVISE,
        BLOCKED
    }

    private static final int APPROVED_PLAN_MAX_CHARS = 1200;

    private final Status status;
    private final String approvedPlan;
    private final String feedback;
    private final String reason;

    private ReviewDecision(Status status, String approvedPlan, String feedback, String reason) {
        this.status = Objects.requireNonNull(status, "status");
        this.approvedPlan = approvedPlan;
        this.feedback = feedback;
        this.reason = reason;
    }

    public static ReviewDecision approved(String approvedPlan) {
        return new ReviewDecision(Status.APPROVED, limit(approvedPlan, APPROVED_PLAN_MAX_CHARS), null, null);
    }

    public static ReviewDecision revise(String feedback) {
        return new ReviewDecision(Status.REVISE, null, trim(feedback), null);
    }

    public static ReviewDecision blocked(String reason) {
        return new ReviewDecision(Status.BLOCKED, null, null, trim(reason));
    }

    public Status status() {
        return status;
    }

    public String approvedPlan() {
        return approvedPlan;
    }

    public String feedback() {
        return feedback;
    }

    public String reason() {
        return reason;
    }

    private static String limit(String value, int maxChars) {
        String trimmed = trim(value);
        if (trimmed == null || trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReviewDecision)) {
            return false;
        }
        ReviewDecision that = (ReviewDecision) other;
        return status == that.status
                && Objects.equals(approvedPlan, that.approvedPlan)
                && Objects.equals(feedback, that.feedback)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, approvedPlan, feedback, reason);
    }

    @Override
    public String toString() {
        return "ReviewDecision{"
                + "status=" + status
                + ", approvedPlanLength=" + lengthOf(approvedPlan)
                + ", feedbackLength=" + lengthOf(feedback)
                + ", reasonLength=" + lengthOf(reason)
                + '}';
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
