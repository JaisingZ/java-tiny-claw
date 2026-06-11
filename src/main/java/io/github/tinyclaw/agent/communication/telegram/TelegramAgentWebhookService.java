package io.github.tinyclaw.agent.communication.telegram;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.communication.approval.ApprovalManager;
import io.github.tinyclaw.agent.communication.approval.ToolApprovalMiddleware;
import io.github.tinyclaw.agent.communication.ChatAgentService;
import io.github.tinyclaw.agent.communication.ChatMessage;
import io.github.tinyclaw.agent.communication.WorkspaceSerialExecutor;
import io.github.tinyclaw.agent.observability.FileTraceSink;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.AgentToolRegistries;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.SessionManager;
import io.github.tinyclaw.agent.runtime.WorkingMemoryPolicy;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.permission.PermissionFileWatcher;
import io.github.tinyclaw.agent.tool.permission.PermissionPolicyProvider;
import io.github.tinyclaw.agent.tool.permission.PermissionPolicySnapshot;
import io.github.tinyclaw.agent.tool.permission.ToolPermissionConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Telegram webhook 模式的库式 Agent 宿主。
 */
public final class TelegramAgentWebhookService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramAgentWebhookService.class);

    private final TelegramWebhookConfig telegramConfig;
    private final LmStudioConfig lmStudioConfig;
    private final Path workDir;
    private final int maxSteps;
    private final boolean enableThinking;
    private final boolean planMode;
    private final boolean debug;
    private final WorkingMemoryPolicy workingMemoryPolicy;
    private final ToolPermissionConfig toolPermissionConfig;
    private final ApprovalManager approvalManager;
    private final TunnelFactory tunnelFactory;
    private final RegistrarFactory registrarFactory;
    private final PermissionPolicyProvider permissionPolicyProvider;
    private WorkspaceSerialExecutor executor;
    private TelegramTransport transport;
    private PublicTunnel tunnel;
    private PermissionFileWatcher permissionFileWatcher;

    public TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, false, false, new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null), new ApprovalManager(),
                TryCloudflareTunnel::start, TelegramWebhookRegistrar::new);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, TunnelFactory tunnelFactory,
            RegistrarFactory registrarFactory) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, false, false, new WorkingMemoryPolicy(),
                ToolPermissionConfig.from(null), new ApprovalManager(), tunnelFactory, registrarFactory);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, boolean planMode,
            WorkingMemoryPolicy workingMemoryPolicy,
            TunnelFactory tunnelFactory, RegistrarFactory registrarFactory) {
        this(telegramConfig, lmStudioConfig, workDir, maxSteps, enableThinking, planMode, false, workingMemoryPolicy,
                ToolPermissionConfig.from(null), new ApprovalManager(), tunnelFactory, registrarFactory);
    }

    TelegramAgentWebhookService(TelegramWebhookConfig telegramConfig, LmStudioConfig lmStudioConfig,
            Path workDir, int maxSteps, boolean enableThinking, boolean planMode,
            boolean debug,
            WorkingMemoryPolicy workingMemoryPolicy, ToolPermissionConfig toolPermissionConfig,
            ApprovalManager approvalManager, TunnelFactory tunnelFactory, RegistrarFactory registrarFactory) {
        this.telegramConfig = Objects.requireNonNull(telegramConfig, "telegramConfig");
        this.lmStudioConfig = Objects.requireNonNull(lmStudioConfig, "lmStudioConfig");
        this.workDir = Objects.requireNonNull(workDir, "workDir");
        this.maxSteps = maxSteps;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
        this.debug = debug;
        this.workingMemoryPolicy = Objects.requireNonNull(workingMemoryPolicy, "workingMemoryPolicy");
        this.toolPermissionConfig = Objects.requireNonNull(toolPermissionConfig, "toolPermissionConfig");
        this.approvalManager = Objects.requireNonNull(approvalManager, "approvalManager");
        this.tunnelFactory = Objects.requireNonNull(tunnelFactory, "tunnelFactory");
        this.registrarFactory = Objects.requireNonNull(registrarFactory, "registrarFactory");
        this.permissionPolicyProvider = createPermissionPolicyProvider(this.workDir, this.toolPermissionConfig);
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
                agentConfig.debug(),
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
        startPermissionWatcher();
        executor = new WorkspaceSerialExecutor();
        ChatAgentService service = new ChatAgentService(this::createEngine, TelegramRunLogger::new, executor,
                new SessionManager(workingMemoryPolicy), approvalCommandsEnabled() ? approvalManager : null);
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
        if (permissionFileWatcher != null) {
            permissionFileWatcher.close();
            permissionFileWatcher = null;
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
        LmStudioModelProvider provider = createProvider();
        ToolRegistry registry = AgentToolRegistries.mainRegistry(provider, workDir);
        if (permissionsEnabled()) {
            registry.use(new ToolApprovalMiddleware(permissionPolicyProvider, approvalManager,
                    message.chatId(), session));
        }
        runLogger.engineStarted(workDir, lmStudioConfig.model(), maxSteps, enableThinking, registry.definitions());
        return new AgentEngine(provider, registry, maxSteps, enableThinking, runLogger,
                workDir, planMode, stateDir(message), TraceRecorder.forSink(new FileTraceSink(workDir)));
    }

    private LmStudioModelProvider createProvider() {
        if (!debug) {
            return new LmStudioModelProvider(lmStudioConfig);
        }
        return new LmStudioModelProvider(lmStudioConfig, line -> LOGGER.info("{}", line));
    }

    private boolean permissionsEnabled() {
        return permissionPolicyProvider.current().enabled();
    }

    private boolean approvalCommandsEnabled() {
        return toolPermissionConfig.hotReload() || permissionsEnabled();
    }

    private void startPermissionWatcher() {
        if (!toolPermissionConfig.hotReload()) {
            permissionPolicyProvider.reload();
            return;
        }
        if (permissionFileWatcher == null) {
            permissionFileWatcher = new PermissionFileWatcher(permissionPolicyProvider,
                    toolPermissionConfig.reloadInterval());
        }
        permissionFileWatcher.start();
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

    private static PermissionPolicyProvider createPermissionPolicyProvider(Path workDir, ToolPermissionConfig config) {
        Path permissionFile = resolvePermissionFile(workDir, config.permissionFile());
        PermissionPolicyProvider.SnapshotLoader loader = () -> {
            if (Files.exists(permissionFile)) {
                return PermissionPolicySnapshot.load(permissionFile);
            }
            if (config.legacyConfigured()) {
                return PermissionPolicySnapshot.fromLegacyConfig(config, permissionFile);
            }
            return PermissionPolicySnapshot.disabled(permissionFile);
        };
        return new PermissionPolicyProvider(permissionFile, loader.load(), loader);
    }

    private static Path resolvePermissionFile(Path workDir, Path permissionFile) {
        if (permissionFile.isAbsolute()) {
            return permissionFile;
        }
        return workDir.resolve(permissionFile).normalize();
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
