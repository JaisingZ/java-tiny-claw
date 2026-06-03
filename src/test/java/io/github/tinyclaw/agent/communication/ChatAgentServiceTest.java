package io.github.tinyclaw.agent.communication;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 通信调度服务测试。
 */
class ChatAgentServiceTest {

    /**
     * 文本消息会被转换为 chat-* 任务并通过会话回传最终回答。
     */
    @Test
    void handlesTextMessageAsChatTaskAndSendsAnswer() throws Exception {
        RecordingSession session = new RecordingSession();
        AtomicReference<String> taskId = new AtomicReference<String>();
        AtomicReference<String> goal = new AtomicReference<String>();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                logger -> new AgentEngine(new EchoFinishProvider(taskId, goal), new ToolRegistry(), 2, false, logger),
                TelegramStyleRunLogger::new,
                executor);

        service.handle(new ChatMessage("m1", "chat-a", "user-a", "summarize README"), session);

        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(taskId.get()).isEqualTo("chat-m1");
        assertThat(goal.get()).isEqualTo("summarize README");
        assertThat(session.messages()).containsExactly("answer:summarize README");
    }

    /**
     * 空文本不启动 Agent，直接返回错误。
     */
    @Test
    void rejectsBlankMessageBeforeStartingAgent() throws Exception {
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                logger -> {
                    throw new AssertionError("Agent must not start for blank messages");
                },
                TelegramStyleRunLogger::new,
                executor);

        service.handle(new ChatMessage("m2", "chat-a", "user-a", "   "), session);

        assertThat(executor.awaitIdle(200, TimeUnit.MILLISECONDS)).isTrue();
        executor.close();
        assertThat(session.errors()).containsExactly("消息内容为空，无法启动 Agent。");
    }

    /**
     * 创建 AgentEngine 失败也应回传错误，而不是泄漏在线程中。
     */
    @Test
    void sendsErrorWhenEngineFactoryFails() throws Exception {
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                logger -> {
                    throw new IllegalStateException("provider missing");
                },
                TelegramStyleRunLogger::new,
                executor);

        service.handle(new ChatMessage("m3", "chat-a", "user-a", "hello"), session);

        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(session.errors()).containsExactly("Agent 调度失败：provider missing");
    }

    private static final class EchoFinishProvider implements ModelProvider {
        private final AtomicReference<String> taskId;
        private final AtomicReference<String> goal;

        private EchoFinishProvider(AtomicReference<String> taskId, AtomicReference<String> goal) {
            this.taskId = taskId;
            this.goal = goal;
        }

        @Override
        public Decision decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools) {
            taskId.set(context.taskId());
            goal.set(context.goal());
            return new FinishDecision("answer:" + context.goal());
        }
    }

    private static final class TelegramStyleRunLogger extends AbstractChatRunLogger {
        private TelegramStyleRunLogger(ChatSession session) {
            super(session);
        }
    }

    private static final class RecordingSession implements ChatSession {
        private final List<String> messages = new ArrayList<String>();
        private final List<String> errors = new ArrayList<String>();

        @Override
        public void sendText(String text) {
            messages.add(text);
        }

        @Override
        public void sendStatus(String text) {
            messages.add(text);
        }

        @Override
        public void sendError(String text) {
            errors.add(text);
        }

        private List<String> messages() {
            return messages;
        }

        private List<String> errors() {
            return errors;
        }
    }
}
