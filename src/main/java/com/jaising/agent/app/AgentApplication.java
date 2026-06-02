package com.jaising.agent.app;

import com.jaising.agent.domain.Task;
import com.jaising.agent.provider.LmStudioConfig;
import com.jaising.agent.provider.LmStudioModelProvider;
import com.jaising.agent.runtime.AgentEngine;
import com.jaising.agent.runtime.ConsoleRunLogger;
import com.jaising.agent.runtime.RunLogger;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.BashTool;
import com.jaising.agent.tool.EditFileTool;
import com.jaising.agent.tool.ReadFileTool;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.WriteFileTool;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * 应用入口
 * 负责命令行入口分发
 */
public final class AgentApplication {

    private static final String RUN_COMMAND = "run";
    private static final String STARTUP_CHECK_COMMAND = "startup-check";
    private static final String LIVE_FLAG = "--live";
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
        if (args.length == 0) {
            startHarness();
            return;
        }
        if (STARTUP_CHECK_COMMAND.equals(args[0])) {
            MainLoopStartupCheck.run(hasLiveFlag(args));
            return;
        }
        if (RUN_COMMAND.equals(args[0])) {
            runPrompt(RunOptions.parse(args));
            return;
        }
        throw new IllegalArgumentException("Unknown command: " + args[0]);
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
        logger.writeLine("java-tiny-claw agent harness provider=" + config.model()
                + " tools=" + registry.definitions().size());
    }

    private static boolean hasLiveFlag(String[] args) {
        for (int i = 1; i < args.length; i++) {
            if (LIVE_FLAG.equalsIgnoreCase(args[i])) {
                return true;
            }
            throw new IllegalArgumentException("Unknown startup-check option: " + args[i]);
        }
        return false;
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
        InMemoryTraceRecorder traceRecorder = new InMemoryTraceRecorder();
        LmStudioModelProvider provider = options.debug()
                ? new LmStudioModelProvider(config, runLogger::writeLine)
                : new LmStudioModelProvider(config);
        runLogger.engineStarted(workDir, config.model(), options.maxSteps(), options.thinking(),
                registry.definitions());
        AgentEngine engine = new AgentEngine(provider, registry, new InMemoryStateStore(), traceRecorder,
                options.maxSteps(), options.thinking(), runLogger);

        RunResult result = engine.run(new Task("cli-" + UUID.randomUUID(), options.prompt()));

        emitRunOutput(runLogger, result, traceRecorder.events(), options.debug());
    }

    private static void registerTool(ToolRegistry registry, Tool tool, RunLogger runLogger) {
        registry.register(tool);
        runLogger.registryMounted(tool.name());
    }

    static void emitRunOutput(RunLogger logger, RunResult result, List<TraceEvent> events, boolean debug) {
        if (!debug) {
            emitTrace(logger, events);
        }
        emitResult(logger, result);
        if (!debug) {
            emitObservations(logger, result);
        }
    }

    private static void emitTrace(RunLogger logger, List<TraceEvent> events) {
        logger.writeLine("TRACE");
        for (TraceEvent event : events) {
            logger.writeLine(event.type()
                    + " step=" + event.step()
                    + " durationMs=" + event.durationMillis()
                    + " detail=" + event.detail());
        }
    }

    private static void emitResult(RunLogger logger, RunResult result) {
        logger.writeLine("RESULT status=" + result.state().status()
                + " answer=" + result.state().finalAnswer()
                + " failure=" + result.state().failureReason());
    }

    private static void emitObservations(RunLogger logger, RunResult result) {
        logger.writeLine("OBSERVATIONS");
        for (String observation : result.state().observations()) {
            logger.writeLine(observation);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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
