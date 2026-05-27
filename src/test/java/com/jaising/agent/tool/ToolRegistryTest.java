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
        registry.register(new WeatherTool());

        List<ToolDefinition> definitions = registry.definitions();

        assertThat(definitions).containsExactly(new ToolDefinition(
                "get_weather",
                "查询城市天气",
                Collections.<String, Object>singletonMap("type", "object")));
    }

    @Test
    void executesRegisteredTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new WeatherTool());

        ToolResult result = registry.execute(new ToolCall("get_weather",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.success("sunny"));
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

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
    }

    private Map<String, Object> writeArguments(String path, String content) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("content", content);
        return arguments;
    }

    private String printFileCommand(String path) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "Get-Content '" + path + "'";
        }
        return "cat '" + path + "'";
    }

    private static final class WeatherTool implements Tool {
        @Override
        public String name() {
            return "get_weather";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(name(), "查询城市天气",
                    Collections.<String, Object>singletonMap("type", "object"));
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success("sunny");
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
