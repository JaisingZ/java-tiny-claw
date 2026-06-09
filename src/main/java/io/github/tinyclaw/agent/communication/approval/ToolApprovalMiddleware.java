package io.github.tinyclaw.agent.communication.approval;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolRegistry.ToolExecution;
import io.github.tinyclaw.agent.tool.ToolRegistry.ToolMiddleware;
import io.github.tinyclaw.agent.tool.ToolResult;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionDecision;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionPolicy;
import java.time.Duration;
import java.util.Objects;

/**
 * 基于权限策略和聊天审批的工具中间件。
 */
public final class ToolApprovalMiddleware implements ToolMiddleware {

    private final ToolPermissionPolicy policy;
    private final ApprovalManager approvalManager;
    private final String chatId;
    private final ChatSession session;
    private final Duration approvalTimeout;

    public ToolApprovalMiddleware(ToolPermissionPolicy policy, ApprovalManager approvalManager, String chatId,
            ChatSession session, Duration approvalTimeout) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.approvalManager = Objects.requireNonNull(approvalManager, "approvalManager");
        this.chatId = chatId;
        this.session = Objects.requireNonNull(session, "session");
        this.approvalTimeout = Objects.requireNonNull(approvalTimeout, "approvalTimeout");
    }

    @Override
    public ToolResult execute(ToolCall call, AgentContext context, ToolExecution next) {
        ToolPermissionDecision decision = policy.evaluate(call);
        if (decision.isAllow()) {
            return next.execute(call, context);
        }
        if (decision.isDeny()) {
            return ToolResult.failure("permission_denied: " + decision.reason());
        }
        ApprovalResult result = approvalManager.requestApproval(chatId, session, call, approvalTimeout);
        if (result.allowed()) {
            return next.execute(call, context);
        }
        return ToolResult.failure("approval_rejected: " + result.reason());
    }
}
