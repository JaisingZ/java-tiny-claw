package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.tool.BashTool;
import io.github.tinyclaw.agent.tool.EditFileTool;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.WriteFileTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 主循环与真实工具的组合测试
 */
class AgentEngineIntegrationTest {

    @TempDir
    Path workDir;

    /**
     * 真实 write_file 和 bash 工具可以组合完成任务
     */
    @Test
    void runsWriteThenBashThenFinishes() {
        EngineFixture fixture = fixture(new WriteFileTool(workDir), new BashTool(workDir, Duration.ofSeconds(5), 8_000));

        RunResult result = fixture.run(new WriteThenBashProvider(), "task-write-bash", "write and read");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(2);
        assertThat(result.observations().get(1)).contains("hello");
        assertThat(result.finalAnswer()).isEqualTo("done");
    }

    /**
     * 真实 bash 工具的非零退出码仍作为观测返回 并明确暴露 exitCode
     */
    @Test
    void recordsNonZeroBashExitCodeAsObservation() {
        EngineFixture fixture = fixture(new BashTool(workDir, Duration.ofSeconds(5), 8_000));

        RunResult result = fixture.run(new FailingBashThenFinishProvider(), "task-bash-exit-code", "fail once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0)).contains("exitCode=7", "boom");
    }

    /**
     * 真实 edit_file 工具可以局部修改文件
     */
    @Test
    void runsWriteThenEditThenFinishes() throws Exception {
        EngineFixture fixture = fixture(new WriteFileTool(workDir), new EditFileTool(workDir));

        RunResult result = fixture.run(new WriteThenEditProvider(), "task-write-edit", "write and edit");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(2);
        assertThat(result.observations().get(1)).contains("line_by_line_trim");
        assertThat(Files.readString(workDir.resolve("Server.java"))).contains("throw new IllegalStateException()");
    }

    private EngineFixture fixture(Tool... tools) {
        return new EngineFixture(tools);
    }

    /**
     * 先写文件 再用命令读取
     */
    private static final class WriteThenBashProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            if (state.observations().isEmpty()) {
                java.util.Map<String, Object> arguments = new java.util.LinkedHashMap<String, Object>();
                arguments.put("path", "hello.txt");
                arguments.put("content", "hello");
                return new ToolDecision(new ToolCall("write_file", arguments));
            }
            if (state.observations().size() == 1) {
                return new ToolDecision(new ToolCall("bash",
                        Collections.<String, Object>singletonMap("command", printFileCommand("hello.txt"))));
            }
            return new FinishDecision("done");
        }

        private String printFileCommand(String path) {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                return "Get-Content '" + path + "'";
            }
            return "cat '" + path + "'";
        }
    }

    /**
     * 先执行失败 bash 再结束
     */
    private static final class FailingBashThenFinishProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            if (state.observations().isEmpty()) {
                return new ToolDecision(new ToolCall("bash",
                        Collections.<String, Object>singletonMap("command", commandThatFails())));
            }
            return new FinishDecision("done");
        }

        private String commandThatFails() {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                return "Write-Output 'boom'; exit 7";
            }
            return "echo boom; exit 7";
        }
    }

    /**
     * 先写文件 再局部编辑
     */
    private static final class WriteThenEditProvider implements ModelProvider {
        @Override
        public Decision decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            if (state.observations().isEmpty()) {
                java.util.Map<String, Object> arguments = new java.util.LinkedHashMap<String, Object>();
                arguments.put("path", "Server.java");
                arguments.put("content", "class Server {\n"
                        + "    void run() {\n"
                        + "        if (user == null) {\n"
                        + "            return;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
                return new ToolDecision(new ToolCall("write_file", arguments));
            }
            if (state.observations().size() == 1) {
                java.util.Map<String, Object> arguments = new java.util.LinkedHashMap<String, Object>();
                arguments.put("path", "Server.java");
                arguments.put("old_text", "if (user == null) {\nreturn;\n}");
                arguments.put("new_text", "if (user == null) {\n    throw new IllegalStateException();\n}");
                return new ToolDecision(new ToolCall("edit_file", arguments));
            }
            return new FinishDecision("done");
        }
    }

    private static final class EngineFixture {
        private final ToolRegistry registry = new ToolRegistry();

        private EngineFixture(Tool... tools) {
            Arrays.stream(tools).forEach(registry::register);
        }

        private RunResult run(ModelProvider provider, String taskId, String goal) {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AgentEngine engine = new AgentEngine(provider, registry, 4, false, NoopRunLogger.INSTANCE, executor);
            try {
                return engine.run(new Task(taskId, goal));
            } finally {
                engine.shutdown();
            }
        }
    }
}
