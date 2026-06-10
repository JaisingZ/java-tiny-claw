package io.github.tinyclaw.agent.tool;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 只读探索子智能体工具。
 */
public final class SubagentTool implements Tool {

    private final SubagentRunner runner;

    public SubagentTool(SubagentRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public String name() {
        return "spawn_subagent";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> promptProperty = new LinkedHashMap<String, Object>();
        promptProperty.put("type", "string");
        promptProperty.put("description", "Focused exploration task for a read-only subagent");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("task_prompt", promptProperty);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("task_prompt"));

        return new ToolDefinition(name(),
                "Spawn a read-only Subagent for deep exploration and return a concise 探索 report.",
                parameters);
    }

    @Override
    public ToolResult execute(ToolCall call, AgentContext context) {
        Object rawPrompt = call.arguments().get("task_prompt");
        if (!(rawPrompt instanceof String) || ((String) rawPrompt).trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: task_prompt");
        }
        ToolResult result;
        try {
            result = runner.run(((String) rawPrompt).trim());
        } catch (RuntimeException ex) {
            return ToolResult.failure("subagent_failed: " + messageOrUnknown(ex.getMessage()));
        }
        if (result == null) {
            return ToolResult.failure("subagent_failed: unknown error");
        }
        if (!result.success()) {
            return ToolResult.failure("subagent_failed: " + messageOrUnknown(result.errorMessage()));
        }
        return ToolResult.success("【子智能体探索报告】\n" + result.output());
    }

    @Override
    public boolean isSideEffect() {
        return false;
    }

    private String messageOrUnknown(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "unknown error";
        }
        return message;
    }
}
