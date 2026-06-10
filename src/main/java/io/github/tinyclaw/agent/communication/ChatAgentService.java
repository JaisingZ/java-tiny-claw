package io.github.tinyclaw.agent.communication;

import io.github.tinyclaw.agent.communication.approval.ApprovalManager;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.AgentSession;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.SessionManager;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通信消息到 Agent Main Loop 的调度层。
 */
public final class ChatAgentService implements ChatMessageHandler {

    private final SessionEngineFactory engineFactory;
    private final Function<ChatSession, RunLogger> runLoggerFactory;
    private final WorkspaceSerialExecutor executor;
    private final SessionManager sessionManager;
    private final ApprovalManager approvalManager;

    public ChatAgentService(Function<RunLogger, AgentEngine> engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor) {
        this(engineFactory, runLoggerFactory, executor, new SessionManager());
    }

    public ChatAgentService(Function<RunLogger, AgentEngine> engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor,
            SessionManager sessionManager) {
        this((runLogger, message) -> engineFactory.apply(runLogger), runLoggerFactory, executor, sessionManager);
    }

    public ChatAgentService(EngineFactory engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor,
            SessionManager sessionManager) {
        this(engineFactory, runLoggerFactory, executor, sessionManager, null);
    }

    public ChatAgentService(EngineFactory engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor,
            SessionManager sessionManager, ApprovalManager approvalManager) {
        this.engineFactory = (runLogger, message, session) -> engineFactory.create(runLogger, message);
        this.runLoggerFactory = Objects.requireNonNull(runLoggerFactory, "runLoggerFactory");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.approvalManager = approvalManager;
    }

    public ChatAgentService(SessionEngineFactory engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor,
            SessionManager sessionManager, ApprovalManager approvalManager) {
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.runLoggerFactory = Objects.requireNonNull(runLoggerFactory, "runLoggerFactory");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.approvalManager = approvalManager;
    }

    @Override
    public void handle(ChatMessage message, ChatSession session) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(session, "session");
        if (!message.hasText()) {
            session.sendError("消息内容为空，无法启动 Agent。");
            return;
        }
        if (approvalManager != null && approvalManager.resolveCommand(message.chatId(), message.text(), session)) {
            return;
        }

        executor.submit(() -> runAgent(message, session));
    }

    private void runAgent(ChatMessage message, ChatSession session) {
        AgentEngine engine = null;
        try {
            RunLogger runLogger = runLoggerFactory.apply(session);
            engine = engineFactory.create(runLogger, message, session);
            AgentSession agentSession = sessionManager.getOrCreate(sessionKey(message));
            engine.run(agentSession, new Task("chat-" + message.messageId(), message.text()));
        } catch (RuntimeException ex) {
            session.sendError("Agent 调度失败：" + ex.getMessage());
        } finally {
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    private String sessionKey(ChatMessage message) {
        if (hasText(message.chatId())) {
            return "chat:" + message.chatId().trim();
        }
        if (hasText(message.senderId())) {
            return "sender:" + message.senderId().trim();
        }
        return "message:" + message.messageId();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    public interface EngineFactory {

        AgentEngine create(RunLogger runLogger, ChatMessage message);
    }

    @FunctionalInterface
    public interface SessionEngineFactory {

        AgentEngine create(RunLogger runLogger, ChatMessage message, ChatSession session);
    }
}
