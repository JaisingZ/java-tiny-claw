package io.github.tinyclaw.agent.communication;

import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.RunLogger;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通信消息到 Agent Main Loop 的调度层。
 */
public final class ChatAgentService implements ChatMessageHandler {

    private final Function<RunLogger, AgentEngine> engineFactory;
    private final Function<ChatSession, RunLogger> runLoggerFactory;
    private final WorkspaceSerialExecutor executor;

    public ChatAgentService(Function<RunLogger, AgentEngine> engineFactory,
            Function<ChatSession, RunLogger> runLoggerFactory, WorkspaceSerialExecutor executor) {
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.runLoggerFactory = Objects.requireNonNull(runLoggerFactory, "runLoggerFactory");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public void handle(ChatMessage message, ChatSession session) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(session, "session");
        if (!message.hasText()) {
            session.sendError("消息内容为空，无法启动 Agent。");
            return;
        }

        executor.submit(() -> runAgent(message, session));
    }

    private void runAgent(ChatMessage message, ChatSession session) {
        RunLogger runLogger = runLoggerFactory.apply(session);
        AgentEngine engine = engineFactory.apply(runLogger);
        try {
            engine.run(new Task("chat-" + message.messageId(), message.text()));
        } catch (RuntimeException ex) {
            session.sendError("Agent 调度失败：" + ex.getMessage());
        } finally {
            engine.shutdown();
        }
    }
}
