package com.jaising.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.tool.BashTool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.WriteFileTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
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
     * 写入 Java 文件 编译并执行。
     */
    @Test
    void writesCompilesAndRunsJavaFileThroughMainLoop() {
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(10), 8_000));

        AgentEngine engine = new AgentEngine(new WriteCompileRunProvider(), registry, 5);

        RunResult result = engine.run(new Task("task-java-smoke", "create and run minimal java file"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.finalAnswer()).isEqualTo("java sample executed");
        assertThat(result.observations()).hasSize(2);
        assertThat(result.observations().get(1)).contains("hello-from-main-loop");
        assertThat(Files.exists(workDir.resolve("HelloFromAgent.java"))).isTrue();
        assertThat(Files.exists(workDir.resolve("HelloFromAgent.class"))).isTrue();
    }

    /**
     * 模拟模型决策 write_file -> bash -> finish。
     */
    private static final class WriteCompileRunProvider implements ModelProvider {

        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools) {
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
                    + "        System.err.println(\"hello-from-main-loop\");\n"
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
