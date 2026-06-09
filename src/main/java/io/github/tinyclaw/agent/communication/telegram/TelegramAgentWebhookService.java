package io.github.tinyclaw.agent.communication.telegram;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.communication.approval.ApprovalManager;
import io.github.tinyclaw.agent.communication.approval.ToolApprovalMiddleware;
import io.github.tinyclaw.agent.communication.ChatAgentService;
import io.github.tinyclaw.agent.communication.ChatMessage;
import io.github.tinyclaw.agent.communication.WorkspaceSerialExecutor;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.SessionManager;
import io.github.tinyclaw.agent.runtime.WorkingMemoryPolicy;
import io.github.tinyclaw.agent.tool.BashTool;
import io.github.tinyclaw.agent.tool.EditFileTool;
import io.github.tinyclaw.agent.tool.ReadFileTool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.WriteFileTool;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionConfig;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionPolicy;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Telegram webhook 模式的库式 Agent 宿主。
 */
public final class TelegramAgentWebhookService implements AutoCloseable {

    private final TelegramWebhookConfig telegramConfig;
    private final LmStudioConfig lmStudioConfig;
    private final Path workDir;
    private final int maxSteps;
    private final boolean enableThinking;
    private final boolean planMode;
    private final WorkingMemoryPolicy workingMemoryPolicy;
    private final ToolPermissionConfig toolPermissionConfig;
    private final ApprovalManager approvalManager;
    private final TunnelFactory tunnelFactory;
    private final RegistrarFactory registrarFactory;
    private WorkspaceSerialExecutor executor;
    private TelegramTransport transport;
    private PublicTunnel tunnel;

    public TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, false, new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null), new ApprovalManager(),
                TryCloudflareTunnel::start, TelegramWebhookRegistrar::new);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, TunnelFactory tunnelFactory,
            RegistrarFactory registrarFactory) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, false, new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null), new ApprovalManager(), tunnelFactory, registrarFactory);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, boolean planMode,
            WorkingMemoryPolicy workingMemoryPolicy,
            TunnelFactory tunnelFactory, RegistrarFactory registrarFactory) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, planMode, workingMemoryPolicy,
                ToolPermissionConfig.from(null), new ApprovalManager(), tunnelFactory, registrarFactory);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, boolean planMode,
            WorkingMemoryPolicy workingMemoryPolicy, ToolPermissionConfig toolPermissionConfig,
            ApprovalManager approvalManager, TunnelFactory tunnelFactory, RegistrarFactory registrarFactory) {
        this.telegramConfig = Objects.requireNonNull(telegramConfig, "telegramConfig");
        this.lmStudioConfig = Objects.requireNonNull(lmStudioConfig, "lmStudioConfig");
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
        this.workingMemoryPolicy = Objects.requireNonNull(workingMemoryPolicy, "workingMemoryPolicy");
        this.toolPermissionConfig = Objects.requireNonNull(toolPermissionConfig, "toolPermissionConfig");
        this.approvalManager = Objects.requireNonNull(approvalManager, "approvalManager");
        this.tunnelFactory = Objects.requireNonNull(tunnelFactory, "tunnelFactory");
        this.registrarFactory = Objects.requireNonNull(registrarFactory, "registrarFactory");
    }

    public static TelegramAgentWebhookService loadDefault() {
        TelegramAgentConfig agentConfig = TelegramAgentConfig.loadDefault();
        return new TelegramAgentWebhookService(
                TelegramWebhookConfig.loadDefault(),
                LmStudioConfig.loadDefault(),
                agentConfig.workDir(),
                agentConfig.maxSteps(),
                agentConfig.enableThinking(),
                agentConfig.planMode(),
                agentConfig.workingMemoryPolicy(),
                agentConfig.toolPermissionConfig(),
                new ApprovalManager(),
                TryCloudflareTunnel::start,
                TelegramWebhookRegistrar::new);
    }

    public void start() {
        if (transport != null) {
            return;
        }
        executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(this::createEngine, TelegramRunLogger::new, executor,
                new SessionManager(workingMemoryPolicy), permissionsEnabled() ? approvalManager : null);
        if (usesTryCloudflareTunnel()) {
            startWithTryCloudflare(service);
            return;
        }
        transport = new TelegramTransport(telegramConfig,
                chatId -> new TelegramSession(telegramConfig.token(), chatId),
                registrarFactory.create(telegramConfig));
        transport.start(service);
    }

    public int listenPort() {
        if (transport == null) {
            return telegramConfig.listenPort();
        }
        return transport.listenPort();
    }

    @Override
    public void close() {
        stop();
    }

    public void stop() {
        if (transport != null) {
            transport.stop();
            transport = null;
        }
        if (tunnel != null) {
            tunnel.close();
            tunnel = null;
        }
        if (executor != null) {
            executor.close();
            executor = null;
        }
    }

    private void startWithTryCloudflare(ChatAgentService service) {
        TelegramWebhookConfig localConfig = telegramConfig.withPublicWebhookUrl("");
        try {
            transport = new TelegramTransport(localConfig);
            transport.start(service);
            tunnel = tunnelFactory.start(transport.listenPort());
            if (telegramConfig.registrationDelaySeconds() > 0) {
                sleepSeconds(telegramConfig.registrationDelaySeconds(), "registration delay");
            }
            String publicWebhookUrl = joinUrl(tunnel.publicBaseUrl(), telegramConfig.webhookPath());
            registerWithRetries(telegramConfig.withPublicWebhookUrl(publicWebhookUrl));
        } catch (RuntimeException ex) {
            stop();
            throw ex;
        }
    }

    private void registerWithRetries(TelegramWebhookConfig config) {
        int maxAttempts = config.registrationMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                registrarFactory.create(config).register();
                return;
            } catch (IllegalStateException ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                if (config.registrationRetryIntervalSeconds() > 0) {
                    sleepSeconds(config.registrationRetryIntervalSeconds(), "setWebhook retry");
                }
            }
        }
    }

    private AgentEngine createEngine(RunLogger runLogger, ChatMessage message, ChatSession session) {
        ToolRegistry registry = new ToolRegistry()
                .register(new ReadFileTool(workDir))
                .register(new WriteFileTool(workDir))
                .register(new EditFileTool(workDir))
                .register(new BashTool(workDir));
        if (permissionsEnabled()) {
            registry.use(new ToolApprovalMiddleware(new ToolPermissionPolicy(toolPermissionConfig), approvalManager,
                    message.chatId(), session, toolPermissionConfig.approvalTimeout()));
        }
        runLogger.engineStarted(workDir, lmStudioConfig.model(), maxSteps, enableThinking, registry.definitions());
        return new AgentEngine(new LmStudioModelProvider(lmStudioConfig), registry, maxSteps, enableThinking, runLogger,
                workDir, planMode, stateDir(message));
    }

    private boolean permissionsEnabled() {
        return toolPermissionConfig.enabled();
    }

    private Path stateDir(ChatMessage message) {
        return Path.of(".tinyclaw", "state", "chat", stateSegment(message));
    }

    private String stateSegment(ChatMessage message) {
        if (hasText(message.chatId())) {
            return sanitizePathSegment(message.chatId());
        }
        if (hasText(message.senderId())) {
            return "sender-" + sanitizePathSegment(message.senderId());
        }
        return "message-" + sanitizePathSegment(message.messageId());
    }

    private String sanitizePathSegment(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return hasText(sanitized) ? sanitized : "unknown";
    }

    private boolean usesTryCloudflareTunnel() {
        return !hasText(telegramConfig.publicWebhookUrl())
                && "trycloudflare".equals(telegramConfig.tunnel().toLowerCase(Locale.ROOT));
    }

    private static String joinUrl(String publicBaseUrl, String path) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void sleepSeconds(int seconds, String context) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + context, ex);
        }
    }

    interface PublicTunnel extends AutoCloseable {

        String publicBaseUrl();

        @Override
        void close();
    }

    @FunctionalInterface
    interface TunnelFactory {

        PublicTunnel start(int listenPort);
    }

    @FunctionalInterface
    interface RegistrarFactory {

        TelegramWebhookRegistrar create(TelegramWebhookConfig config);
    }
}
