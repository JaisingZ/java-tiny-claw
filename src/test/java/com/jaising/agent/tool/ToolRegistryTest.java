package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolRegistryTest {

    @TempDir
    Path workDir;

    @Test
    void exposesRegisteredToolDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadOnlyTool());

        List<ToolDefinition> definitions = registry.definitions();

        assertThat(definitions).containsExactly(new ToolDefinition(
                "read_only",
                "只读工具",
                Collections.<String, Object>singletonMap("type", "object")));
    }

    @Test
    void executesRegisteredTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadOnlyTool());

        ToolResult result = registry.execute(new ToolCall("read_only",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.success("ok"));
    }

    @Test
    void returnsFailureWhenToolIsUnknown() {
        ToolRegistry registry = new ToolRegistry();

        ToolResult result = registry.execute(new ToolCall("missing",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("Unknown tool: missing"));
    }

    @Test
    void wrapsToolExceptionAsFailure() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new BrokenTool());

        ToolResult result = registry.execute(new ToolCall("broken",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("tool_error: boom"));
    }

    @Test
    void executesWriteAndBashTools() {
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(5), 8_000));

        ToolResult writeResult = registry.execute(new ToolCall("write_file",
                writeArguments("hello.txt", "hello")), state());
        ToolResult bashResult = registry.execute(new ToolCall("bash",
                Collections.<String, Object>singletonMap("command", printFileCommand("hello.txt"))), state());

        assertThat(writeResult.success()).isTrue();
        assertThat(bashResult.success()).isTrue();
        assertThat(bashResult.output()).contains("hello");
    }

    @Test
    void executesEditFileTool() throws Exception {
        java.nio.file.Files.writeString(workDir.resolve("hello.txt"), "hello old world");
        ToolRegistry registry = new ToolRegistry()
                .register(new EditFileTool(workDir));

        ToolResult result = registry.execute(new ToolCall("edit_file",
                editArguments("hello.txt", "old", "new")), state());

        assertThat(result.success()).isTrue();
        assertThat(java.nio.file.Files.readString(workDir.resolve("hello.txt"))).isEqualTo("hello new world");
    }

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
    }

    private Map<String, Object> writeArguments(String path, String content) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("content", content);
        return arguments;
    }

    private Map<String, Object> editArguments(String path, String oldText, String newText) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("old_text", oldText);
        arguments.put("new_text", newText);
        return arguments;
    }

    private String printFileCommand(String path) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "Get-Content '" + path + "'";
        }
        return "cat '" + path + "'";
    }

    private static final class ReadOnlyTool implements Tool {
        @Override
        public String name() {
            return "read_only";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(name(), "只读工具",
                    Collections.<String, Object>singletonMap("type", "object"));
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success("ok");
        }

        @Override
        public boolean isSideEffect() {
            return false;
        }
    }

    private static final class BrokenTool implements Tool {
        @Override
        public String name() {
            return "broken";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            throw new RuntimeException("boom");
        }
    }
}
