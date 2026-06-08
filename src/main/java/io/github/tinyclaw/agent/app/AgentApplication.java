package io.github.tinyclaw.agent.app;

import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.communication.telegram.TelegramAgentWebhookService;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.Slf4jRunLogger;
import io.github.tinyclaw.agent.tool.BashTool;
import io.github.tinyclaw.agent.tool.EditFileTool;
import io.github.tinyclaw.agent.tool.ReadFileTool;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.WriteFileTool;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.UUID;

/**
 * 应用入口，负责命令行分发和运行时组装。
 */
public final class AgentApplication {

    private static final String RUN_COMMAND = "run";
    private static final String TELEGRAM_COMMAND = "telegram";

    private AgentApplication() {
    }

    /**
     * 程序入口。
     */
    public static void main(String[] args) throws Exception {
        // main 只保留最薄的入口，便于 exec-maven-plugin 和测试共享同一分发逻辑。
        execute(args);
    }

    static void execute(String[] args) throws Exception {
        StartupMode mode = resolveStartupMode(args);
        if (mode == StartupMode.RUN_PROMPT) {
            runPrompt(RunOptions.parse(args));
            return;
        }
        startTelegram();
    }

    static StartupMode resolveStartupMode(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing command: run or telegram");
        }
        if (RUN_COMMAND.equals(args[0])) {
            return StartupMode.RUN_PROMPT;
        }
        if (TELEGRAM_COMMAND.equals(args[0])) {
            ensureNoExtraArgs(args);
            return StartupMode.TELEGRAM;
        }
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void startTelegram() throws InterruptedException {
        // Webhook 服务是长驻进程；这里主动阻塞，直到 JVM 收到外部停止信号。
        TelegramAgentWebhookService service = TelegramAgentWebhookService.loadDefault();
        Runtime.getRuntime().addShutdownHook(new Thread(service::stop, "telegram-webhook-shutdown"));
        try {
            service.start();
            new CountDownLatch(1).await();
        } finally {
            service.stop();
        }
    }

    private static void ensureNoExtraArgs(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Unknown telegram option: " + args[1]);
        }
    }

    private static void runPrompt(RunOptions options) {
        // CLI run 每次独立组装 provider、tools 和 prompt composer，避免污染 Telegram 会话配置。
        LmStudioConfig config = LmStudioConfig.loadDefault();
        RunLogger runLogger = new Slf4jRunLogger(options.debug());
        Path workDir = Path.of(".");
        ToolRegistry registry = new ToolRegistry();
        registerTool(registry, new ReadFileTool(workDir), runLogger);
        registerTool(registry, new WriteFileTool(workDir), runLogger);
        registerTool(registry, new EditFileTool(workDir), runLogger);
        registerTool(registry, new BashTool(workDir), runLogger);
        LmStudioModelProvider provider = options.debug()
                ? new LmStudioModelProvider(config, runLogger::writeLine)
                : new LmStudioModelProvider(config);
        PromptComposer promptComposer = new DefaultPromptComposer(workDir, options.planMode(), cliStateDir());
        runLogger.engineStarted(workDir, config.model(), options.maxSteps(), options.thinking(),
                registry.definitions());
        AgentEngine engine = new AgentEngine(provider, registry, options.maxSteps(), options.thinking(), runLogger,
                promptComposer, workDir);

        RunResult result = engine.run(new Task("cli-" + UUID.randomUUID(), options.prompt()));

        writeRunOutput(runLogger, result, options.debug());
    }

    private static void registerTool(ToolRegistry registry, Tool tool, RunLogger runLogger) {
        registry.register(tool);
        runLogger.registryMounted(tool.name());
    }

    private static void writeRunOutput(RunLogger logger, RunResult result, boolean debug) {
        logger.writeLine("RESULT status=" + result.status()
                + " answer=" + result.finalAnswer()
                + " failure=" + result.failureReason());
        if (debug) {
            return;
        }
        logger.writeLine("OBSERVATIONS");
        for (String observation : result.observations()) {
            logger.writeLine(observation);
        }
    }

    private static Path cliStateDir() {
        return Path.of(".tinyclaw", "state", "cli", "default");
    }
}
