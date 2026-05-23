package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
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
}
