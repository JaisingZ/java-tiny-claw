package com.jaising.agent.app;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.provider.SiliconFlowConfig;
import com.jaising.agent.provider.SiliconFlowModelProvider;
import com.jaising.agent.runtime.AgentEngine;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.BashTool;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.tool.WriteFileTool;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
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

        System.out.println("=== Main Loop Startup Check ===");
        System.out.println("workDir=" + WORK_ROOT);
        System.out.println("live=" + live);

        runSingleStageFinish();
        runTwoStageThinkingAction();
        runRealToolsWriteBash();
        runFailureModes();
        if (live) {
            runLiveSiliconFlow();
        }

        System.out.println("=== Main Loop Startup Check: PASSED ===");
    }

    private static void runSingleStageFinish() {
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        AgentEngine engine = new AgentEngine(new FinishOnlyProvider(), new ToolRegistry(),
                new InMemoryStateStore(), trace, 2);

        RunResult result = engine.run(new Task("startup-single-stage", "finish directly"));

        printCase("single-stage-finish", trace.events(), result);

        requireStatus(result, AgentStatus.SUCCESS);
        require("single-stage-ready".equals(result.state().finalAnswer()), "single-stage final answer mismatch");
        requireTraceOrder(trace.events(), TraceEventType.MODEL_REQUEST, TraceEventType.MODEL_RESPONSE,
                TraceEventType.FINISHED);
        requireTraceContains(trace.events(), TraceEventType.MODEL_RESPONSE, "FinishDecision answer=single-stage-ready");
    }

    private static void runTwoStageThinkingAction() {
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry().register(new EchoTool());
        ThinkingThenActionProvider provider = new ThinkingThenActionProvider();
        AgentEngine engine = new AgentEngine(provider, registry, new InMemoryStateStore(), trace, 4, true);

        RunResult result = engine.run(new Task("startup-thinking-action", "think then echo"));

        printCase("two-stage-thinking-action", trace.events(), result);

        requireStatus(result, AgentStatus.SUCCESS);
        require(result.state().observations().contains("hello-startup"), "thinking action observation missing");
        require(provider.phases().equals(Arrays.asList(DecisionPhase.THINKING, DecisionPhase.ACTION,
                DecisionPhase.THINKING, DecisionPhase.ACTION)), "unexpected decision phases: " + provider.phases());
        require(provider.toolsByPhase().get(0).isEmpty(), "thinking phase should not see tools");
        require(provider.toolsByPhase().get(1).size() == 1
                && "echo".equals(provider.toolsByPhase().get(1).get(0).name()), "action phase should see echo");
        requireTraceOrder(trace.events(), TraceEventType.THINKING_REQUEST, TraceEventType.THINKING_RESPONSE,
                TraceEventType.MODEL_REQUEST, TraceEventType.MODEL_RESPONSE, TraceEventType.TOOL_CALL,
                TraceEventType.TOOL_RESULT, TraceEventType.FINISHED);
        requireTraceContains(trace.events(), TraceEventType.THINKING_REQUEST, "tools=[]");
        requireTraceContains(trace.events(), TraceEventType.MODEL_REQUEST, "tools=[echo]");
        requireTraceContains(trace.events(), TraceEventType.MODEL_RESPONSE, "ToolDecision tool=echo");
    }

    private static void runRealToolsWriteBash() throws IOException {
        Path workDir = WORK_ROOT.resolve("real-tools-write-bash");
        resetDirectory(workDir);
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(10), 8_000));
        AgentEngine engine = new AgentEngine(new WriteCompileRunProvider(), registry,
                new InMemoryStateStore(), trace, 5);

        RunResult result = engine.run(new Task("startup-real-tools", "write compile run"));

        printCase("real-tools-write-bash", trace.events(), result);

        requireStatus(result, AgentStatus.SUCCESS);
        require("java sample executed".equals(result.state().finalAnswer()), "real tools final answer mismatch");
        require(result.state().observations().size() == 2, "real tools should record two observations");
        require(result.state().observations().get(1).contains("hello-from-startup"), "java output missing");
        require(Files.exists(workDir.resolve("HelloFromStartup.java")), "java source was not written");
        require(Files.exists(workDir.resolve("HelloFromStartup.class")), "java class was not compiled");
        requireTraceContains(trace.events(), TraceEventType.MODEL_REQUEST, "tools=[write_file, bash]");
        requireTraceContains(trace.events(), TraceEventType.TOOL_RESULT, "success=true");
    }

    private static void runFailureModes() {
        runFailure("failure-missing-tool", new ToolOnlyProvider("missing"),
                new ToolRegistry(), "Unknown tool: missing", 4);
        runFailure("failure-tool-error", new ToolOnlyProvider("fail_tool"),
                new ToolRegistry().register(new FailingTool()), "startup tool failed", 4);
        runFailure("failure-provider-error", new ThrowingProvider(),
                new ToolRegistry(), "provider_error: startup provider failed", 4);
        runFailure("failure-max-steps", new ToolOnlyProvider("echo"),
                new ToolRegistry().register(new EchoTool()), "max_steps_exceeded", 1);
    }

    private static void runFailure(String caseName, ModelProvider provider, ToolRegistry registry,
            String failureReason, int maxSteps) {
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        AgentEngine engine = new AgentEngine(provider, registry, new InMemoryStateStore(), trace, maxSteps);

        RunResult result = engine.run(new Task(caseName, caseName));

        printCase(caseName, trace.events(), result);

        requireStatus(result, AgentStatus.FAILED);
        require(failureReason.equals(result.state().failureReason()),
                "expected failure '" + failureReason + "' but was '" + result.state().failureReason() + "'");
        requireTraceContains(trace.events(), TraceEventType.FAILED, "reason=" + failureReason);
    }

    private static void runLiveSiliconFlow() {
        SiliconFlowConfig config = SiliconFlowConfig.loadDefault();
        require(hasText(config.apiKey()), "siliconflow.apiKey is empty");

        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        AgentEngine engine = new AgentEngine(
                new SiliconFlowModelProvider(config, System.out),
                new ToolRegistry(),
                new InMemoryStateStore(),
                trace,
                1,
                true);

        RunResult result = engine.run(new Task("startup-live-siliconflow",
                "请直接用中文回答：Main Loop live 启动测试完成。不要调用工具。"));

        printCase("live-siliconflow", trace.events(), result);

        requireStatus(result, AgentStatus.SUCCESS);
        requireTraceContains(trace.events(), TraceEventType.THINKING_RESPONSE, "ThinkingDecision thought=");
        requireTraceContains(trace.events(), TraceEventType.MODEL_RESPONSE, "FinishDecision answer=");
    }

    private static void printCase(String caseName, List<TraceEvent> events, RunResult result) {
        System.out.println("CASE " + caseName);
        System.out.println("TRACE");
        for (TraceEvent event : events) {
            System.out.println(event.type()
                    + " step=" + event.step()
                    + " durationMs=" + event.durationMillis()
                    + " detail=" + event.detail());
        }
        System.out.println("RESULT status=" + result.state().status()
                + " answer=" + result.state().finalAnswer()
                + " failure=" + result.state().failureReason());
    }

    private static void requireStatus(RunResult result, AgentStatus status) {
        require(result.state().status() == status,
                "expected status " + status + " but was " + result.state().status()
                        + " failure=" + result.state().failureReason());
    }

    private static void requireTraceContains(List<TraceEvent> events, TraceEventType type, String text) {
        for (TraceEvent event : events) {
            if (event.type() == type && event.detail().contains(text)) {
                return;
            }
        }
        throw new IllegalStateException("Missing trace " + type + " containing: " + text);
    }

    private static void requireTraceOrder(List<TraceEvent> events, TraceEventType... expectedTypes) {
        int index = 0;
        for (TraceEvent event : events) {
            if (event.type() == expectedTypes[index]) {
                index++;
                if (index == expectedTypes.length) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Trace order missing: " + Arrays.toString(expectedTypes));
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
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
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
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
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
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
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
                    + "        System.out.println(\"hello-from-startup\");\n"
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
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            return new ToolDecision(new ToolCall(toolName,
                    Collections.<String, Object>singletonMap("text", "hello-startup")));
        }
    }

    /**
     * 固定抛出 provider 异常。
     */
    private static final class ThrowingProvider implements ModelProvider {
        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
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
        public ToolResult execute(ToolCall call, AgentState state) {
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
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.failure("startup tool failed");
        }
    }
}
