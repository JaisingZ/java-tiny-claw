package io.github.tinyclaw.agent.benchmark;

import io.github.tinyclaw.agent.context.DefaultPromptComposer;
import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.observability.FileTraceSink;
import io.github.tinyclaw.agent.observability.TraceRecorder;
import io.github.tinyclaw.agent.provider.LmStudioConfig;
import io.github.tinyclaw.agent.provider.LmStudioModelProvider;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.AgentSession;
import io.github.tinyclaw.agent.runtime.AgentToolRegistries;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.runtime.RunMetrics;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.runtime.RunStatus;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 执行 benchmark suite：准备靶场、运行 Agent、执行验证脚本并汇总指标。
 */
public final class BenchmarkRunner {

    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_OUTPUT_CHARS = 8_000;

    private final Path benchRoot;
    private final AgentRunner agentRunner;
    private final CommandExecutor commandExecutor;

    public BenchmarkRunner(Path benchRoot, AgentRunner agentRunner, CommandExecutor commandExecutor) {
        this.benchRoot = normalize(benchRoot);
        this.agentRunner = Objects.requireNonNull(agentRunner, "agentRunner");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
    }

    public static BenchmarkRunner lmStudio(Path projectRoot, LmStudioConfig config, RunLogger runLogger) {
        ModelProvider provider = new LmStudioModelProvider(config);
        AgentRunner runner = new DefaultAgentRunner(provider, runLogger);
        return new BenchmarkRunner(projectRoot.resolve(".tinyclaw").resolve("bench"), runner,
                new ShellCommandExecutor(COMMAND_TIMEOUT));
    }

    public BenchmarkReport runSuite(List<BenchmarkCase> benchmarkCases) {
        List<BenchmarkResult> results = new ArrayList<BenchmarkResult>();
        List<BenchmarkCase> safeCases = benchmarkCases == null
                ? java.util.Collections.<BenchmarkCase>emptyList()
                : benchmarkCases;
        for (int i = 0; i < safeCases.size(); i++) {
            results.add(runCase(safeCases.get(i), i));
        }
        return BenchmarkReport.fromResults(results);
    }

    private BenchmarkResult runCase(BenchmarkCase benchmarkCase, int index) {
        long startMillis = System.currentTimeMillis();
        Path workDir = workspaceFor(benchmarkCase, index);
        Path traceDir = workDir.resolve(".tinyclaw").resolve("traces");
        try {
            Files.createDirectories(workDir);
        } catch (IOException ex) {
            return result(benchmarkCase, false, "workspace setup failed: " + ex.getMessage(), startMillis,
                    RunMetrics.empty(), traceDir, "", 0);
        }

        if (hasText(benchmarkCase.setupCommand())) {
            CommandResult setup = commandExecutor.execute(workDir, benchmarkCase.setupCommand());
            if (!setup.success()) {
                return result(benchmarkCase, false, "setup failed: " + trimOutput(setup.output()), startMillis,
                        RunMetrics.empty(), traceDir, setup.output(), 0);
            }
        }

        AgentSession session = new AgentSession(benchmarkCase.id());
        RunResult runResult = agentRunner.run(workDir, benchmarkCase, session);

        CommandResult validation = commandExecutor.execute(workDir, benchmarkCase.validateCommand());
        if (!validation.success()) {
            String errorMessage = "validation failed: " + trimOutput(validation.output());
            if (runResult.status() != RunStatus.SUCCESS) {
                errorMessage = "agent failed: " + runResult.failureReason() + "; " + errorMessage;
            }
            return result(benchmarkCase, false, errorMessage, startMillis,
                    runResult.metrics(), traceDir, validation.output(), runResult.stepCount());
        }

        return result(benchmarkCase, true, "", startMillis, runResult.metrics(), traceDir,
                validation.output(), runResult.stepCount());
    }

    private BenchmarkResult result(BenchmarkCase benchmarkCase, boolean passed, String errorMessage, long startMillis,
            RunMetrics metrics, Path traceDir, String validationOutput, int turnsToSuccess) {
        return new BenchmarkResult(benchmarkCase.id(), benchmarkCase.name(), passed, errorMessage,
                System.currentTimeMillis() - startMillis, metrics, traceDir.toString(),
                trimOutput(validationOutput), turnsToSuccess);
    }

    private Path workspaceFor(BenchmarkCase benchmarkCase, int index) {
        String safeId = benchmarkCase.id().replaceAll("[^A-Za-z0-9._-]", "_");
        return benchRoot.resolve("workspaces")
                .resolve(safeId + "-" + System.currentTimeMillis() + "-" + index)
                .toAbsolutePath()
                .normalize();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static Path normalize(Path path) {
        return (path == null ? Path.of(".tinyclaw", "bench") : path).toAbsolutePath().normalize();
    }

    private static String trimOutput(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        if (trimmed.length() <= MAX_OUTPUT_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_OUTPUT_CHARS) + "\n[output truncated]";
    }

    @FunctionalInterface
    public interface AgentRunner {
        RunResult run(Path workDir, BenchmarkCase benchmarkCase, AgentSession session);
    }

    @FunctionalInterface
    public interface CommandExecutor {
        CommandResult execute(Path workDir, String command);
    }

    private static final class DefaultAgentRunner implements AgentRunner {
        private final ModelProvider provider;
        private final RunLogger runLogger;

        private DefaultAgentRunner(ModelProvider provider, RunLogger runLogger) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.runLogger = Objects.requireNonNull(runLogger, "runLogger");
        }

        @Override
        public RunResult run(Path workDir, BenchmarkCase benchmarkCase, AgentSession session) {
            ToolRegistry registry = AgentToolRegistries.mainRegistry(provider, workDir);
            PromptComposer promptComposer = new DefaultPromptComposer(
                    workDir, false, workDir.resolve(".tinyclaw").resolve("state"));
            AgentEngine engine = new AgentEngine(provider, registry, benchmarkCase.maxSteps(),
                    benchmarkCase.enableThinking(), runLogger, promptComposer, workDir,
                    TraceRecorder.forSink(new FileTraceSink(workDir)));
            return engine.run(session, new Task("bench-" + benchmarkCase.id(), benchmarkCase.taskPrompt()));
        }
    }

    private static final class ShellCommandExecutor implements CommandExecutor {
        private final Duration timeout;

        private ShellCommandExecutor(Duration timeout) {
            this.timeout = timeout;
        }

        @Override
        public CommandResult execute(Path workDir, String command) {
            Process process;
            try {
                process = processBuilder(workDir, command).start();
            } catch (IOException ex) {
                return CommandResult.failed(-1, "Failed to start command: " + ex.getMessage());
            }

            CompletableFuture<String> outputFuture = readOutputAsync(process.getInputStream());
            boolean finished;
            try {
                finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return CommandResult.failed(-1, "Command interrupted");
            }

            if (!finished) {
                process.destroyForcibly();
                return CommandResult.failed(-1, outputFuture.join()
                        + "\n[Command timed out after " + timeout.toMillis() + " ms and was terminated]");
            }

            return new CommandResult(process.exitValue(), outputFuture.join());
        }

        private ProcessBuilder processBuilder(Path workDir, String command) {
            ProcessBuilder builder;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                builder = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", command);
            } else {
                builder = new ProcessBuilder("bash", "-c", command);
            }
            builder.directory(workDir.toFile());
            builder.redirectErrorStream(true);
            return builder;
        }

        private String readOutput(InputStream inputStream) {
            try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return "Failed to read command output: " + ex.getMessage();
            }
        }

        private CompletableFuture<String> readOutputAsync(InputStream inputStream) {
            CompletableFuture<String> future = new CompletableFuture<String>();
            Thread reader = new Thread(() -> future.complete(readOutput(inputStream)),
                    "benchmark-command-output-reader");
            reader.setDaemon(true);
            reader.start();
            return future;
        }
    }
}
