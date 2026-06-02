package com.jaising.agent.app;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.provider.LmStudioConfig;
import com.jaising.agent.provider.LmStudioModelProvider;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.runtime.AgentEngine;
import com.jaising.agent.runtime.ConsoleRunLogger;
import com.jaising.agent.runtime.RunLogger;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.runtime.RunStatus;
import com.jaising.agent.tool.BashTool;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.tool.WriteFileTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Loop 真实启动自检。
 */
final class MainLoopStartupCheck {

    private static final Path WORK_ROOT = Path.of("target", "main-loop-startup-work")
            .toAbsolutePath().normalize();

    private MainLoopStartupCheck() {
    }

    /**
     * 运行 Main Loop 启动自检。
     */
    static void run(boolean live) throws Exception {
        resetDirectory(WORK_ROOT);
        RunLogger logger = ConsoleRunLogger.standardOutput(false);

        logger.writeLine("=== Main Loop Startup Check ===");
        logger.writeLine("workDir=" + WORK_ROOT);
        logger.writeLine("live=" + live);

        runSingleStageFinish(logger);
        runTwoStageThinkingAction(logger);
        runRealToolsWriteBash(logger);
        runFailureModes(logger);
        if (live) {
            runLiveLmStudio(logger);
        }

        logger.writeLine("=== Main Loop Startup Check: PASSED ===");
    }

    private static void runSingleStageFinish(RunLogger logger) {
        AgentEngine engine = new AgentEngine(new FinishOnlyProvider(), new ToolRegistry(), 2);

        RunResult result = engine.run(new Task("startup-single-stage", "finish directly"));

        emitCase(logger, "single-stage-finish", result);

        requireStatus(result, RunStatus.SUCCESS);
        require("single-stage-ready".equals(result.finalAnswer()), "single-stage final answer mismatch");
    }

    private static void runTwoStageThinkingAction(RunLogger logger) {
        ToolRegistry registry = new ToolRegistry().register(new EchoTool());
        ThinkingThenActionProvider provider = new ThinkingThenActionProvider();
        AgentEngine engine = new AgentEngine(provider, registry, 4, true);

        RunResult result = engine.run(new Task("startup-thinking-action", "think then echo"));

        emitCase(logger, "two-stage-thinking-action", result);

        requireStatus(result, RunStatus.SUCCESS);
        require(result.observations().contains("hello-startup"), "thinking action observation missing");
        require(provider.phases().equals(Arrays.asList(DecisionPhase.THINKING, DecisionPhase.ACTION,
                DecisionPhase.THINKING, DecisionPhase.ACTION)), "unexpected decision phases: " + provider.phases());
        require(provider.toolsByPhase().get(0).isEmpty(), "thinking phase should not see tools");
        require(provider.toolsByPhase().get(1).size() == 1
                && "echo".equals(provider.toolsByPhase().get(1).get(0).name()), "action phase should see echo");
    }

    private static void runRealToolsWriteBash(RunLogger logger) throws IOException {
        Path workDir = WORK_ROOT.resolve("real-tools-write-bash");
        resetDirectory(workDir);
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(10), 8_000));
        AgentEngine engine = new AgentEngine(new WriteCompileRunProvider(), registry, 5);

        RunResult result = engine.run(new Task("startup-real-tools", "write compile run"));

        emitCase(logger, "real-tools-write-bash", result);

        requireStatus(result, RunStatus.SUCCESS);
        require("java sample executed".equals(result.finalAnswer()), "real tools final answer mismatch");
        require(result.observations().size() == 2, "real tools should record two observations");
        require(result.observations().get(1).contains("hello-from-startup"), "java output missing");
        require(Files.exists(workDir.resolve("HelloFromStartup.java")), "java source was not written");
        require(Files.exists(workDir.resolve("HelloFromStartup.class")), "java class was not compiled");
    }

    private static void runFailureModes(RunLogger logger) {
        runFailure(logger, "failure-missing-tool", new ToolOnlyProvider("missing"),
                new ToolRegistry(), "Unknown tool: missing", 4);
        runFailure(logger, "failure-tool-error", new ToolOnlyProvider("fail_tool"),
                new ToolRegistry().register(new FailingTool()), "startup tool failed", 4);
        runFailure(logger, "failure-provider-error", new ThrowingProvider(),
                new ToolRegistry(), "provider_error: startup provider failed", 4);
        runFailure(logger, "failure-max-steps", new ToolOnlyProvider("echo"),
                new ToolRegistry().register(new EchoTool()), "max_steps_exceeded", 1);
    }

    private static void runFailure(RunLogger logger, String caseName, ModelProvider provider, ToolRegistry registry,
            String failureReason, int maxSteps) {
        AgentEngine engine = new AgentEngine(provider, registry, maxSteps);

        RunResult result = engine.run(new Task(caseName, caseName));

        emitCase(logger, caseName, result);

        requireStatus(result, RunStatus.FAILED);
        require(failureReason.equals(result.failureReason()),
                "expected failure '" + failureReason + "' but was '" + result.failureReason() + "'");
    }

    private static void runLiveLmStudio(RunLogger logger) {
        LmStudioConfig config = LmStudioConfig.loadDefault();
        require(hasText(config.model()), "lmstudio.model is empty");

        AgentEngine engine = new AgentEngine(
                new LmStudioModelProvider(config, logger::writeLine),
                new ToolRegistry(),
                1,
                true);

        RunResult result = engine.run(new Task("startup-live-lmstudio",
                "请直接用中文回答：Main Loop live 启动测试完成。不要调用工具。"));

        emitCase(logger, "live-lmstudio", result);

        requireStatus(result, RunStatus.SUCCESS);
        require(hasText(result.finalAnswer()), "live final answer is empty");
    }

    private static void emitCase(RunLogger logger, String caseName, RunResult result) {
        logger.writeLine("CASE " + caseName);
        logger.writeLine("RESULT status=" + result.status()
                + " answer=" + result.finalAnswer()
                + " failure=" + result.failureReason());
    }

    private static void requireStatus(RunResult result, RunStatus status) {
        require(result.status() == status,
                "expected status " + status + " but was " + result.status()
                        + " failure=" + result.failureReason());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void resetDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(MainLoopStartupCheck::deletePath);
            }
        }
        Files.createDirectories(path);
    }

    private static void deletePath(Path path) {
        try {
            Files.delete(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete " + path + ": " + ex.getMessage(), ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 直接返回完成决策。
     */
    private static final class FinishOnlyProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new FinishDecision("single-stage-ready");
        }
    }

    /**
     * 慢思考后先调用工具 再结束。
     */
    private static final class ThinkingThenActionProvider implements ModelProvider {
        private final List<DecisionPhase> phases = new ArrayList<DecisionPhase>();
        private final List<List<ToolDefinition>> toolsByPhase = new ArrayList<List<ToolDefinition>>();

        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            phases.add(phase);
            toolsByPhase.add(availableTools);
            if (phase == DecisionPhase.THINKING) {
                return new ThinkingDecision(state.observations().isEmpty()
                        ? "plan to echo"
                        : "plan to finish");
            }
            if (state.observations().isEmpty()) {
                return new ToolDecision(new ToolCall("echo",
                        Collections.<String, Object>singletonMap("text", "hello-startup")));
            }
            return new FinishDecision("thinking-action-ready");
        }

        private List<DecisionPhase> phases() {
            return phases;
        }

        private List<List<ToolDefinition>> toolsByPhase() {
            return toolsByPhase;
        }
    }

    /**
     * write_file -> bash -> finish。
     */
    private static final class WriteCompileRunProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            if (state.observations().isEmpty()) {
                Map<String, Object> arguments = new LinkedHashMap<String, Object>();
                arguments.put("path", "HelloFromStartup.java");
                arguments.put("content", javaSource());
                return new ToolDecision(new ToolCall("write_file", arguments));
            }
            if (state.observations().size() == 1) {
                return new ToolDecision(new ToolCall("bash",
                        Collections.<String, Object>singletonMap("command", compileAndRunCommand())));
            }
            return new FinishDecision("java sample executed");
        }

        private static String javaSource() {
            return "public class HelloFromStartup {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        System.err.println(\"hello-from-startup\");\n"
                    + "    }\n"
                    + "}\n";
        }

        private static String compileAndRunCommand() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                return "javac HelloFromStartup.java; "
                        + "if ($LASTEXITCODE -eq 0) { java HelloFromStartup } else { exit $LASTEXITCODE }";
            }
            return "javac HelloFromStartup.java && java HelloFromStartup";
        }
    }

    /**
     * 固定请求一个工具。
     */
    private static final class ToolOnlyProvider implements ModelProvider {
        private final String toolName;

        private ToolOnlyProvider(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new ToolDecision(new ToolCall(toolName,
                    Collections.<String, Object>singletonMap("text", "hello-startup")));
        }
    }

    /**
     * 固定抛出 provider 异常。
     */
    private static final class ThrowingProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            throw new RuntimeException("startup provider failed");
        }
    }

    /**
     * 简单回显工具。
     */
    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.success(String.valueOf(call.arguments().get("text")));
        }
    }

    /**
     * 固定失败工具。
     */
    private static final class FailingTool implements Tool {
        @Override
        public String name() {
            return "fail_tool";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.failure("startup tool failed");
        }
    }
}
