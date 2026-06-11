package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.communication.ChatMessage;
import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.communication.approval.ApprovalManager;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.NoopRunLogger;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.WorkingMemoryPolicy;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelegramAgentWebhookServiceTest {

    @Test
    void createsChatScopedStateDirWhenPlanModeEnabled(@TempDir Path workDir) throws Exception {
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(new ArrayList<String>());
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                true,
                new WorkingMemoryPolicy(),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                registrarFactory);

        AgentEngine engine = createEngineForTest(service, new ChatMessage("m1", "chat-abc", "user-a", "check state dir"));
        try {
            String prompt = composePromptForTest(engine, workDir);
            assertThat(prompt).contains("Plan Mode: State Externalization");
            assertThat(prompt).contains("状态目录（相对于当前工作区）：" + stateDir("chat-abc"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void isolatesPlanStateByChatId(@TempDir Path workDir) throws Exception {
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(new ArrayList<String>());
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                true,
                new WorkingMemoryPolicy(),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                registrarFactory);

        AgentEngine first = createEngineForTest(service, new ChatMessage("m1", "chat-a", "user-a", "first chat"));
        AgentEngine second = createEngineForTest(service, new ChatMessage("m2", "chat-b", "user-a", "second chat"));
        try {
            String firstPrompt = composePromptForTest(first, workDir);
            String secondPrompt = composePromptForTest(second, workDir);

            String firstStateDir = stateDir("chat-a");
            String secondStateDir = stateDir("chat-b");

            assertThat(firstPrompt).contains("状态目录（相对于当前工作区）：" + firstStateDir);
            assertThat(secondPrompt).contains("状态目录（相对于当前工作区）：" + secondStateDir);
            assertThat(firstStateDir).isNotEqualTo(secondStateDir);
        } finally {
            first.shutdown();
            second.shutdown();
        }
    }

    @Test
    void mountsPermissionMiddlewareWhenPermissionsEnabled(@TempDir Path workDir) throws Exception {
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.enabled", "true");
        values.put("agent.permissions.denyPattern.1", "(?i)echo");
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                new TelegramWebhookConfig("token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40),
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                false,
                false,
                new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(values),
                new ApprovalManager(() -> "approval-1"),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                new RecordingRegistrarFactory(new ArrayList<String>()));
        AgentEngine engine = createEngineForTest(service, new ChatMessage("m1", "chat-a", "user-a", "run"),
                new RecordingSession());
        try {
            ToolResult result = registryForTest(engine).execute(new ToolCall("bash", bashArguments("echo hi")),
                    AgentContext.create(new Task("t1", "run")));

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("permission_denied");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void mountsPermissionMiddlewareFromYamlPolicy(@TempDir Path workDir) throws Exception {
        Path policyFile = workDir.resolve(".tinyclaw").resolve("permissions.yaml");
        Files.createDirectories(policyFile.getParent());
        Files.writeString(policyFile, """
                enabled: true
                defaultAction: allow
                rules:
                  - id: deny-echo
                    tools: [bash]
                    action: deny
                    arguments:
                      command:
                        regex: '(?i)echo'
                """);
        Map<String, String> values = new HashMap<String, String>();
        values.put("agent.permissions.file", ".tinyclaw/permissions.yaml");
        values.put("agent.permissions.hotReload", "false");
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                new TelegramWebhookConfig("token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40),
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                false,
                false,
                new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(values),
                new ApprovalManager(() -> "approval-1"),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                new RecordingRegistrarFactory(new ArrayList<String>()));
        AgentEngine engine = createEngineForTest(service, new ChatMessage("m1", "chat-a", "user-a", "run"),
                new RecordingSession());
        try {
            ToolResult result = registryForTest(engine).execute(new ToolCall("bash", bashArguments("echo hi")),
                    AgentContext.create(new Task("t1", "run")));

            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("permission_denied");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void createsProviderWithDebugSinkWhenDebugEnabled(@TempDir Path workDir) throws Exception {
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                new TelegramWebhookConfig("token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40),
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                false,
                true,
                new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null),
                new ApprovalManager(),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                new RecordingRegistrarFactory(new ArrayList<String>()));
        AgentEngine engine = createEngineForTest(service, new ChatMessage("m1", "chat-a", "user-a", "run"),
                new RecordingSession());
        try {
            Object provider = providerForTest(engine);

            assertThat(provider).isInstanceOf(LmStudioModelProvider.class);
            assertThat(debugSinkForTest(provider)).isNotNull();
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void createEngineMountsSpawnSubagent(@TempDir Path workDir) throws Exception {
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                new TelegramWebhookConfig("token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40),
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                workDir,
                2,
                false,
                false,
                false,
                new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null),
                new ApprovalManager(),
                port -> {
                    throw new AssertionError("not expected in createEngine test");
                },
                new RecordingRegistrarFactory(new ArrayList<String>()));
        AgentEngine engine = createEngineForTest(service, new ChatMessage("m1", "chat-a", "user-a", "run"),
                new RecordingSession());
        try {
            assertThat(registryForTest(engine).snapshot()).containsKeys("spawn_subagent");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void startsLocalServerThenTryCloudflareThenRegistersDynamicWebhook() {
        List<String> events = new ArrayList<String>();
        AtomicInteger tunnelPort = new AtomicInteger();
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(events);
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "secret-1", false, 40,
                "trycloudflare", 0, 1, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> {
                    tunnelPort.set(port);
                    events.add("tunnel:" + port);
                    assertThat(port).isPositive();
                    return new FakeTunnel("https://abc-def.trycloudflare.com", events);
                },
                registrarFactory);

        service.start();
        service.stop();

        assertThat(events).containsExactly(
                "tunnel:" + tunnelPort.get(),
                "register:https://abc-def.trycloudflare.com/telegram/webhook",
                "tunnel-close");
        assertThat(registrarFactory.lastConfig().secretToken()).isEqualTo("secret-1");
    }

    @Test
    void usesConfiguredPublicWebhookUrlWithoutStartingTunnel() {
        List<String> events = new ArrayList<String>();
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(events);
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "https://example.com/telegram/webhook", "127.0.0.1", 0,
                "/telegram/webhook", "", false, 40, "trycloudflare");
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> {
                    throw new AssertionError("trycloudflare tunnel must not start when public URL is configured");
                },
                registrarFactory);

        service.start();
        service.stop();

        assertThat(events).containsExactly("register:https://example.com/telegram/webhook");
    }

    @Test
    void closesTunnelWhenDynamicRegistrationFails() {
        List<String> events = new ArrayList<String>();
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40,
                "trycloudflare", 0, 1, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> new FakeTunnel("https://abc-def.trycloudflare.com", events),
                config -> new TelegramWebhookRegistrar(config, (url, body) -> {
                    throw new IllegalStateException("setWebhook failed");
                }));

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setWebhook failed");
        assertThat(events).containsExactly("tunnel-close");
    }

    @Test
    void retriesDynamicWebhookRegistrationWhenTelegramCannotResolveTryCloudflareHost() {
        List<String> events = new ArrayList<String>();
        AtomicInteger attempts = new AtomicInteger();
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40,
                "trycloudflare", 0, 3, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> new FakeTunnel("https://abc-def.trycloudflare.com", events),
                config -> new TelegramWebhookRegistrar(config, (url, body) -> {
                    int attempt = attempts.incrementAndGet();
                    events.add("register-attempt-" + attempt + ":" + config.publicWebhookUrl());
                    if (attempt == 1) {
                        return new TelegramWebhookHttpResponse(400,
                                "{\"ok\":false,\"description\":\"Bad Request: bad webhook: Failed to resolve host: Name or service not known\"}");
                    }
                    return new TelegramWebhookHttpResponse(200, "{\"ok\":true}");
                }));

        service.start();
        service.stop();

        assertThat(events).containsExactly(
                "register-attempt-1:https://abc-def.trycloudflare.com/telegram/webhook",
                "register-attempt-2:https://abc-def.trycloudflare.com/telegram/webhook",
                "tunnel-close");
    }

    private static final class FakeTunnel implements TelegramAgentWebhookService.PublicTunnel {
        private final String publicBaseUrl;
        private final List<String> events;

        private FakeTunnel(String publicBaseUrl, List<String> events) {
            this.publicBaseUrl = publicBaseUrl;
            this.events = events;
        }

        @Override
        public String publicBaseUrl() {
            return publicBaseUrl;
        }

        @Override
        public void close() {
            events.add("tunnel-close");
        }
    }

    private static final class RecordingRegistrarFactory implements TelegramAgentWebhookService.RegistrarFactory {
        private final List<String> events;
        private TelegramWebhookConfig lastConfig;

        private RecordingRegistrarFactory(List<String> events) {
            this.events = events;
        }

        @Override
        public TelegramWebhookRegistrar create(TelegramWebhookConfig config) {
            lastConfig = config;
            return new TelegramWebhookRegistrar(config, (url, body) -> {
                events.add("register:" + config.publicWebhookUrl());
                return new TelegramWebhookHttpResponse(200, "{\"ok\":true}");
            });
        }

        private TelegramWebhookConfig lastConfig() {
            return lastConfig;
        }
    }

    private static AgentEngine createEngineForTest(TelegramAgentWebhookService service, ChatMessage message)
            throws Exception {
        return createEngineForTest(service, message, new RecordingSession());
    }

    private static AgentEngine createEngineForTest(TelegramAgentWebhookService service, ChatMessage message,
            ChatSession session)
            throws Exception {
        Method method = TelegramAgentWebhookService.class.getDeclaredMethod("createEngine", RunLogger.class,
                ChatMessage.class, ChatSession.class);
        method.setAccessible(true);
        return (AgentEngine) method.invoke(service, NoopRunLogger.INSTANCE, message, session);
    }

    private static ToolRegistry registryForTest(AgentEngine engine) throws Exception {
        Field field = AgentEngine.class.getDeclaredField("toolRegistry");
        field.setAccessible(true);
        return (ToolRegistry) field.get(engine);
    }

    private static Object providerForTest(AgentEngine engine) throws Exception {
        Field field = AgentEngine.class.getDeclaredField("provider");
        field.setAccessible(true);
        return field.get(engine);
    }

    private static Object debugSinkForTest(Object provider) throws Exception {
        Field field = LmStudioModelProvider.class.getDeclaredField("debugSink");
        field.setAccessible(true);
        return field.get(provider);
    }

    private static Map<String, Object> bashArguments(String command) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("command", command);
        return arguments;
    }

    private static String composePromptForTest(AgentEngine engine, Path workDir) throws Exception {
        Field field = AgentEngine.class.getDeclaredField("promptComposer");
        field.setAccessible(true);
        Object composer = field.get(engine);
        Method method = composer.getClass().getDeclaredMethod("compose", PromptContext.class);
        return (String) method.invoke(composer, new PromptContext(workDir, DecisionPhase.ACTION, Collections.emptyList()));
    }

    private static String stateDir(String chatId) {
        return ".tinyclaw/state/chat/" + chatId;
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
