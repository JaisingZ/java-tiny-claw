package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

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

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
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
