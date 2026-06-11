package io.github.tinyclaw.agent.app;

import io.github.tinyclaw.agent.benchmark.BenchmarkCase;
import io.github.tinyclaw.agent.benchmark.BenchmarkReport;
import io.github.tinyclaw.agent.benchmark.BenchmarkReportWriter;
import io.github.tinyclaw.agent.benchmark.BenchmarkResult;
import io.github.tinyclaw.agent.benchmark.BenchmarkRunner;
import io.github.tinyclaw.agent.benchmark.BenchmarkSuites;
import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.communication.telegram.TelegramAgentWebhookService;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.observability.FileTraceSink;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.runtime.AgentToolRegistries;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.Slf4jRunLogger;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.UUID;

/**
 * 应用入口，负责命令行分发和运行时组装。
 */
public final class AgentApplication {

    private static final String RUN_COMMAND = "run";
    private static final String TELEGRAM_COMMAND = "telegram";
    private static final String BENCH_COMMAND = "bench";

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
        if (mode == StartupMode.BENCHMARK) {
            runBenchmark();
            return;
        }
        startTelegram();
    }

    static StartupMode resolveStartupMode(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Missing command: run, telegram, or bench");
        }
        if (RUN_COMMAND.equals(args[0])) {
            return StartupMode.RUN_PROMPT;
        }
        if (TELEGRAM_COMMAND.equals(args[0])) {
            ensureNoExtraArgs(args, TELEGRAM_COMMAND);
            return StartupMode.TELEGRAM;
        }
        if (BENCH_COMMAND.equals(args[0])) {
            ensureNoExtraArgs(args, BENCH_COMMAND);
            return StartupMode.BENCHMARK;
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

    private static void ensureNoExtraArgs(String[] args, String command) {
        if (args.length > 1) {
            throw new IllegalArgumentException("Unknown " + command + " option: " + args[1]);
        }
    }

    private static void runBenchmark() {
        LmStudioConfig config = LmStudioConfig.loadDefault();
        RunLogger runLogger = new Slf4jRunLogger(false);
        Path projectRoot = Path.of(".");
        BenchmarkRunner runner = BenchmarkRunner.lmStudio(projectRoot, config, runLogger);
        List<BenchmarkCase> cases = BenchmarkSuites.defaults();
        BenchmarkReport report = runner.runSuite(cases);
        Path reportFile = new BenchmarkReportWriter()
                .write(projectRoot.resolve(".tinyclaw").resolve("bench").resolve("reports"), report);
        writeBenchmarkOutput(runLogger, report, reportFile);
    }

    private static void runPrompt(RunOptions options) {
        // CLI run 每次独立组装 provider、tools 和 prompt composer，避免污染 Telegram 会话配置。
        LmStudioConfig config = LmStudioConfig.loadDefault();
        RunLogger runLogger = new Slf4jRunLogger(options.debug());
        Path workDir = Path.of(".");
        LmStudioModelProvider provider = options.debug()
                ? new LmStudioModelProvider(config, runLogger::writeLine)
                : new LmStudioModelProvider(config);
        ToolRegistry registry = AgentToolRegistries.mainRegistry(provider, workDir);
        logMountedTools(registry, runLogger);
        PromptComposer promptComposer = new DefaultPromptComposer(workDir, options.planMode(), cliStateDir());
        runLogger.engineStarted(workDir, config.model(), options.maxSteps(), options.thinking(),
                registry.definitions());
        AgentEngine engine = new AgentEngine(provider, registry, options.maxSteps(), options.thinking(), runLogger,
                promptComposer, workDir, TraceRecorder.forSink(new FileTraceSink(workDir)));

        RunResult result = engine.run(new Task("cli-" + UUID.randomUUID(), options.prompt()));

        writeRunOutput(runLogger, result, options.debug());
    }

    private static void logMountedTools(ToolRegistry registry, RunLogger runLogger) {
        for (Tool tool : registry.snapshot().values()) {
            runLogger.registryMounted(tool.name());
        }
    }

    private static void writeRunOutput(RunLogger logger, RunResult result, boolean debug) {
        logger.writeLine("RESULT status=" + result.status()
                + " answer=" + result.finalAnswer()
                + " failure=" + result.failureReason());
        logger.writeLine("METRICS modelCalls=" + result.metrics().modelCallCount()
                + " promptTokens=" + result.metrics().promptTokens()
                + " completionTokens=" + result.metrics().completionTokens()
                + " totalTokens=" + result.metrics().totalTokens()
                + " usageUnavailable=" + result.metrics().usageUnavailableCount()
                + " modelDurationMillis=" + result.metrics().modelDurationMillis()
                + " toolCalls=" + result.metrics().toolCallCount()
                + " toolDurationMillis=" + result.metrics().toolDurationMillis());
        if (debug) {
            return;
        }
        logger.writeLine("OBSERVATIONS");
        for (String observation : result.observations()) {
            logger.writeLine(observation);
        }
    }

    private static void writeBenchmarkOutput(RunLogger logger, BenchmarkReport report, Path reportFile) {
        logger.writeLine("BENCHMARK totalCases=" + report.totalCases()
                + " passedCases=" + report.passedCases()
                + " successRate=" + String.format(java.util.Locale.ROOT, "%.2f", report.successRate())
                + " modelCalls=" + report.modelCalls()
                + " promptTokens=" + report.promptTokens()
                + " completionTokens=" + report.completionTokens()
                + " totalTokens=" + report.totalTokens()
                + " usageUnavailable=" + report.usageUnavailableCount()
                + " toolCalls=" + report.toolCalls()
                + " toolFailures=" + report.toolFailureCount()
                + " turnsToSuccess=" + report.turnsToSuccess());
        logger.writeLine("BENCHMARK_REPORT " + reportFile.toAbsolutePath().normalize());
        for (BenchmarkResult result : report.results()) {
            logger.writeLine("CASE id=" + result.caseId()
                    + " passed=" + result.passed()
                    + " durationMillis=" + result.durationMillis()
                    + " modelCalls=" + result.runMetrics().modelCallCount()
                    + " toolCalls=" + result.runMetrics().toolCallCount()
                    + " toolFailures=" + toolFailureCount(result)
                    + " turnsToSuccess=" + result.turnsToSuccess()
                    + " error=" + result.errorMessage());
        }
    }

    private static int toolFailureCount(BenchmarkResult result) {
        int count = 0;
        for (io.github.tinyclaw.agent.runtime.ToolCallMetric metric : result.runMetrics().toolCalls()) {
            if (!metric.success()) {
                count++;
            }
        }
        return count;
    }

    private static Path cliStateDir() {
        return Path.of(".tinyclaw", "state", "cli", "default");
    }
}
