package io.github.tinyclaw.agent.communication;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.communication.approval.ApprovalManager;
import io.github.tinyclaw.agent.communication.approval.ApprovalResult;
import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.provider.ModelUsage;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.SessionManager;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * 相同 chatId 的消息复用同一个 Agent Session。
     */
    @Test
    void reusesAgentSessionForSameChatId() throws Exception {
        RecordingContextProvider provider = new RecordingContextProvider();
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                logger -> new AgentEngine(provider, new ToolRegistry(), 2, false, logger),
                TelegramStyleRunLogger::new,
                executor,
                new SessionManager());

        service.handle(new ChatMessage("m1", "chat-a", "user-a", "first"), session);
        service.handle(new ChatMessage("m2", "chat-a", "user-a", "second"), session);

        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(0).workingMemory()).isEmpty();
        assertThat(provider.contexts().get(1).workingMemory())
                .containsExactly(
                        SessionMessage.user("first"),
                        SessionMessage.assistant("answer:first"));
    }

    /**
     * 不同 chatId 的消息使用不同 Agent Session。
     */
    @Test
    void isolatesAgentSessionsForDifferentChatIds() throws Exception {
        RecordingContextProvider provider = new RecordingContextProvider();
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                logger -> new AgentEngine(provider, new ToolRegistry(), 2, false, logger),
                TelegramStyleRunLogger::new,
                executor,
                new SessionManager());

        service.handle(new ChatMessage("m1", "chat-a", "user-a", "first"), session);
        service.handle(new ChatMessage("m2", "chat-b", "user-a", "second"), session);

        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(0).workingMemory()).isEmpty();
        assertThat(provider.contexts().get(1).workingMemory()).isEmpty();
    }

    @Test
    void engineFactoryCanRoutePlanModeStateByChatId() throws Exception {
        RecordingPromptProvider provider = new RecordingPromptProvider();
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                (logger, message) -> new AgentEngine(provider, new ToolRegistry(), 2, false, logger,
                        new DefaultPromptComposer(Path.of("."), true,
                                Path.of(".tinyclaw", "state", "chat", message.chatId())),
                        Path.of(".")),
                TelegramStyleRunLogger::new,
                executor,
                new SessionManager());

        service.handle(new ChatMessage("m1", "chat-a", "user-a", "first"), session);
        service.handle(new ChatMessage("m2", "chat-b", "user-a", "second"), session);

        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(provider.prompts()).hasSize(2);
        assertThat(provider.prompts().get(0)).contains(".tinyclaw/state/chat/chat-a");
        assertThat(provider.prompts().get(1)).contains(".tinyclaw/state/chat/chat-b");
    }

    @Test
    void resolvesApprovalCommandBeforeSubmittingAgentTask() throws Exception {
        ApprovalManager approvalManager = new ApprovalManager(() -> "approval-chat");
        RecordingSession session = new RecordingSession();
        CompletableFuture<ApprovalResult> approval = CompletableFuture.supplyAsync(
                () -> approvalManager.requestApproval("chat-a", session, bashCall(), Duration.ofSeconds(2)));
        waitForSessionMessage(session, "approval-chat");
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(
                (logger, message) -> {
                    throw new AssertionError("approval command must not start Agent");
                },
                TelegramStyleRunLogger::new,
                executor,
                new SessionManager(),
                approvalManager);

        service.handle(new ChatMessage("m-approve", "chat-a", "user-a", "/approve approval-chat"), session);

        assertThat(approval.get(2, TimeUnit.SECONDS).allowed()).isTrue();
        assertThat(executor.awaitIdle(200, TimeUnit.MILLISECONDS)).isTrue();
        executor.close();
        assertThat(session.messages()).anySatisfy(message -> assertThat(message).contains("已批准：approval-chat"));
    }

    @Test
    void usageCommandReportsCurrentChatSessionMetricsWithoutStartingAgent() throws Exception {
        RecordingSession session = new RecordingSession();
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        SessionManager sessionManager = new SessionManager();
        AtomicInteger providerCalls = new AtomicInteger();
        ModelProvider provider = (context, phase, tools, systemPrompt) -> {
            providerCalls.incrementAndGet();
            return new ModelResponse(new FinishDecision("answer:" + context.goal()),
                    new ModelUsage(12, 4, 16), "model-a", true);
        };
        ChatAgentService service = new ChatAgentService(
                logger -> new AgentEngine(provider, new ToolRegistry(), 2, false, logger),
                TelegramStyleRunLogger::new,
                executor,
                sessionManager);

        service.handle(new ChatMessage("m1", "chat-a", "user-a", "hello"), session);
        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        service.handle(new ChatMessage("m2", "chat-a", "user-a", "/usage"), session);

        assertThat(executor.awaitIdle(200, TimeUnit.MILLISECONDS)).isTrue();
        executor.close();
        assertThat(providerCalls).hasValue(1);
        assertThat(session.messages()).anySatisfy(message -> assertThat(message)
                .contains("当前会话用量")
                .contains("模型调用: 1")
                .contains("Prompt Tokens: 12")
                .contains("Completion Tokens: 4")
                .contains("Total Tokens: 16"));
    }

    private static void waitForSessionMessage(RecordingSession session, String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (session.messages().stream().anyMatch(message -> message.contains(text))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for session message: " + text);
    }

    private static ToolCall bashCall() {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", "git push");
        return new ToolCall("bash", arguments);
    }

    private static final class EchoFinishProvider implements ModelProvider {
        private final AtomicReference<String> taskId;
        private final AtomicReference<String> goal;

        private EchoFinishProvider(AtomicReference<String> taskId, AtomicReference<String> goal) {
            this.taskId = taskId;
            this.goal = goal;
        }

        @Override
        public ModelResponse decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            taskId.set(context.taskId());
            goal.set(context.goal());
            return ModelResponse.of(new FinishDecision("answer:" + context.goal()));
        }
    }

    private static final class RecordingContextProvider implements ModelProvider {
        private final List<AgentContext> contexts = new ArrayList<AgentContext>();

        @Override
        public ModelResponse decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contexts.add(context);
            return ModelResponse.of(new FinishDecision("answer:" + context.goal()));
        }

        private List<AgentContext> contexts() {
            return contexts;
        }
    }

    private static final class RecordingPromptProvider implements ModelProvider {
        private final List<String> prompts = new ArrayList<String>();

        @Override
        public ModelResponse decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            prompts.add(systemPrompt);
            return ModelResponse.of(new FinishDecision("answer:" + context.goal()));
        }

        private List<String> prompts() {
            return prompts;
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
