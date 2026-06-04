package io.github.tinyclaw.agent.app;

import io.github.tinyclaw.agent.communication.telegram.TelegramAgentWebhookService;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.ConsoleRunLogger;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.RunResult;
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
 * 应用入口
 * 负责命令行入口分发
 */
public final class AgentApplication {

    private static final String RUN_COMMAND = "run";
    private static final String TELEGRAM_COMMAND = "telegram";
    private static final String PROMPT_OPTION = "--prompt";
    private static final String THINKING_OPTION = "--thinking";
    private static final String MAX_STEPS_OPTION = "--max-steps";
    private static final String DEBUG_OPTION = "--debug";
    private static final int DEFAULT_MAX_STEPS = 8;

    private AgentApplication() {
    }

    /**
     * 程序入口。
     */
    public static void main(String[] args) throws Exception {
        execute(args, new DefaultApplicationRuntime());
    }

    static void execute(String[] args, ApplicationRuntime runtime) throws Exception {
        if (args.length == 0) {
            if (runtime.telegramWebhookEnabled()) {
                startTelegram(runtime);
                return;
            }
            runtime.startHarness();
            return;
        }
        if (RUN_COMMAND.equals(args[0])) {
            runtime.runPrompt(args);
            return;
        }
        if (TELEGRAM_COMMAND.equals(args[0])) {
            ensureNoExtraArgs(args);
            startTelegram(runtime);
            return;
        }
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }

    private static void startTelegram(ApplicationRuntime runtime) throws Exception {
        ManagedService service = runtime.createTelegramService();
        runtime.addShutdownHook(service::stop);
        try {
            service.start();
            runtime.awaitStop();
        } finally {
            service.stop();
        }
    }

    private static void ensureNoExtraArgs(String[] args) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Unknown telegram option: " + args[1]);
        }
    }

    private static void startHarness() {
        LmStudioConfig config = LmStudioConfig.loadDefault();
        new LmStudioModelProvider(config);
        ToolRegistry registry = new ToolRegistry()
                .register(new ReadFileTool(Path.of(".")))
                .register(new WriteFileTool(Path.of(".")))
                .register(new EditFileTool(Path.of(".")))
                .register(new BashTool(Path.of(".")));
        RunLogger logger = ConsoleRunLogger.standardOutput(false);
        logger.writeLine("Tiny Agent Harness provider=" + config.model()
                + " tools=" + registry.definitions().size());
    }

    private static void runPrompt(RunOptions options) {
        LmStudioConfig config = LmStudioConfig.loadDefault();
        RunLogger runLogger = ConsoleRunLogger.standardOutput(options.debug());
        Path workDir = Path.of(".");
        ToolRegistry registry = new ToolRegistry();
        registerTool(registry, new ReadFileTool(workDir), runLogger);
        registerTool(registry, new WriteFileTool(workDir), runLogger);
        registerTool(registry, new EditFileTool(workDir), runLogger);
        registerTool(registry, new BashTool(workDir), runLogger);
        LmStudioModelProvider provider = options.debug()
                ? new LmStudioModelProvider(config, runLogger::writeLine)
                : new LmStudioModelProvider(config);
        runLogger.engineStarted(workDir, config.model(), options.maxSteps(), options.thinking(),
                registry.definitions());
        AgentEngine engine = new AgentEngine(provider, registry, options.maxSteps(), options.thinking(), runLogger);

        RunResult result = engine.run(new Task("cli-" + UUID.randomUUID(), options.prompt()));

        emitRunOutput(runLogger, result, options.debug());
    }

    private static void registerTool(ToolRegistry registry, Tool tool, RunLogger runLogger) {
        registry.register(tool);
        runLogger.registryMounted(tool.name());
    }

    static void emitRunOutput(RunLogger logger, RunResult result, boolean debug) {
        emitResult(logger, result);
        if (!debug) {
            emitObservations(logger, result);
        }
    }

    private static void emitResult(RunLogger logger, RunResult result) {
        logger.writeLine("RESULT status=" + result.status()
                + " answer=" + result.finalAnswer()
                + " failure=" + result.failureReason());
    }

    private static void emitObservations(RunLogger logger, RunResult result) {
        logger.writeLine("OBSERVATIONS");
        for (String observation : result.observations()) {
            logger.writeLine(observation);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    interface ApplicationRuntime {

        boolean telegramWebhookEnabled();

        void startHarness();

        void runPrompt(String[] args);

        ManagedService createTelegramService();

        void addShutdownHook(Runnable hook);

        void awaitStop() throws InterruptedException;
    }

    interface ManagedService {

        void start();

        void stop();
    }

    private static final class DefaultApplicationRuntime implements ApplicationRuntime {

        @Override
        public boolean telegramWebhookEnabled() {
            return TelegramStartupConfig.loadDefault().enabled();
        }

        @Override
        public void startHarness() {
            AgentApplication.startHarness();
        }

        @Override
        public void runPrompt(String[] args) {
            AgentApplication.runPrompt(RunOptions.parse(args));
        }

        @Override
        public ManagedService createTelegramService() {
            TelegramAgentWebhookService service = TelegramAgentWebhookService.loadDefault();
            return new ManagedService() {
                @Override
                public void start() {
                    service.start();
                }

                @Override
                public void stop() {
                    service.stop();
                }
            };
        }

        @Override
        public void addShutdownHook(Runnable hook) {
            Runtime.getRuntime().addShutdownHook(new Thread(hook, "telegram-webhook-shutdown"));
        }

        @Override
        public void awaitStop() throws InterruptedException {
            new CountDownLatch(1).await();
        }
    }

    private static final class RunOptions {
        private final String prompt;
        private final boolean thinking;
        private final int maxSteps;
        private final boolean debug;

        private RunOptions(String prompt, boolean thinking, int maxSteps, boolean debug) {
            this.prompt = prompt;
            this.thinking = thinking;
            this.maxSteps = maxSteps;
            this.debug = debug;
        }

        private static RunOptions parse(String[] args) {
            String prompt = null;
            boolean thinking = false;
            int maxSteps = DEFAULT_MAX_STEPS;
            boolean debug = false;
            for (int i = 1; i < args.length; i++) {
                if (PROMPT_OPTION.equals(args[i])) {
                    i++;
                    if (i >= args.length) {
                        throw new IllegalArgumentException("Missing value for --prompt");
                    }
                    prompt = args[i];
                } else if (THINKING_OPTION.equals(args[i])) {
                    thinking = true;
                } else if (MAX_STEPS_OPTION.equals(args[i])) {
                    i++;
                    if (i >= args.length) {
                        throw new IllegalArgumentException("Missing value for --max-steps");
                    }
                    maxSteps = parseMaxSteps(args[i]);
                } else if (DEBUG_OPTION.equals(args[i])) {
                    debug = true;
                } else {
                    throw new IllegalArgumentException("Unknown run option: " + args[i]);
                }
            }
            if (!hasText(prompt)) {
                throw new IllegalArgumentException("--prompt is required");
            }
            return new RunOptions(prompt, thinking, maxSteps, debug);
        }

        private static int parseMaxSteps(String value) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed <= 0) {
                    throw new IllegalArgumentException("--max-steps must be positive");
                }
                return parsed;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("--max-steps must be an integer: " + value, ex);
            }
        }

        private String prompt() {
            return prompt;
        }

        private boolean thinking() {
            return thinking;
        }

        private int maxSteps() {
            return maxSteps;
        }

        private boolean debug() {
            return debug;
        }
    }
}
