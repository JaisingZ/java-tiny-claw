package com.jaising.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.middleware.AllowListMiddleware;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.BashTool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.WriteFileTool;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Main Loop 通过 write_file 和 bash 完成最小 Java 开发闭环。
 */
class MainLoopJavaPrimitiveSmokeTest {

    @TempDir
    Path workDir;

    /**
     * 写入 Java 文件 编译并执行 然后打印 trace。
     */
    @Test
    void writesCompilesAndRunsJavaFileThroughMainLoop() {
        InMemoryStateStore store = new InMemoryStateStore();
        InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(10), 8_000));

        AgentEngine engine = new AgentEngine(new WriteCompileRunProvider(), registry,
                Arrays.asList(new AllowListMiddleware(
                        new HashSet<String>(Arrays.asList("write_file", "bash")))),
                store, trace, 5);

        RunResult result = engine.run(new Task("task-java-smoke", "create and run minimal java file"));

        printTrace(trace.events(), result);

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.state().finalAnswer()).isEqualTo("java sample executed");
        assertThat(result.state().observations()).hasSize(2);
        assertThat(result.state().observations().get(1)).contains("hello-from-main-loop");
        assertThat(Files.exists(workDir.resolve("HelloFromAgent.java"))).isTrue();
        assertThat(Files.exists(workDir.resolve("HelloFromAgent.class"))).isTrue();
    }

    private void printTrace(List<TraceEvent> events, RunResult result) {
        System.out.println("=== Main Loop Java Primitive Smoke Trace ===");
        for (TraceEvent event : events) {
            System.out.println(event.type()
                    + " step=" + event.step()
                    + " durationMs=" + event.durationMillis()
                    + " detail=" + event.detail());
        }
        System.out.println("=== Final Observations ===");
        for (String observation : result.state().observations()) {
            System.out.println(observation);
        }
        System.out.println("=== Final State ===");
        System.out.println(result.state().status() + " answer=" + result.state().finalAnswer());
    }

    /**
     * 模拟模型决策 write_file -> bash -> finish。
     */
    private static final class WriteCompileRunProvider implements ModelProvider {

        @Override
        public Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools) {
            if (state.observations().isEmpty()) {
                Map<String, Object> arguments = new LinkedHashMap<String, Object>();
                arguments.put("path", "HelloFromAgent.java");
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
            return "public class HelloFromAgent {\n"
                    + "    public static void main(String[] args) {\n"
                    + "        System.out.println(\"hello-from-main-loop\");\n"
                    + "    }\n"
                    + "}\n";
        }

        private static String compileAndRunCommand() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                return "javac HelloFromAgent.java; if ($LASTEXITCODE -eq 0) { java HelloFromAgent }";
            }
            return "javac HelloFromAgent.java && java HelloFromAgent";
        }
    }
}
