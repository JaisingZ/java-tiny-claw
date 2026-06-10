package io.github.tinyclaw.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubagentToolTest {

    @Test
    void executesRunnerSuccessWithPrefixedReport() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("文件已读取并完成"));

        ToolResult result = tool.execute(new ToolCall("spawn_subagent",
                singletonArgument("task_prompt", "调查日志")), state());

        assertThat(result).isEqualTo(ToolResult.success("【子智能体探索报告】\n文件已读取并完成"));
    }

    @Test
    void rejectsMissingTaskPrompt() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("unused"));

        ToolResult result = tool.execute(new ToolCall("spawn_subagent",
                singletonArgument("wrong_key", "value")), state());

        assertThat(result).isEqualTo(ToolResult.failure("Missing required argument: task_prompt"));
    }

    @Test
    void rejectsBlankTaskPrompt() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("unused"));

        ToolResult result = tool.execute(new ToolCall("spawn_subagent",
                singletonArgument("task_prompt", "   ")), state());

        assertThat(result).isEqualTo(ToolResult.failure("Missing required argument: task_prompt"));
    }

    @Test
    void wrapsRunnerFailureAsSubagentFailure() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.failure("execution timeout"));

        ToolResult result = tool.execute(new ToolCall("spawn_subagent",
                singletonArgument("task_prompt", "调查日志")), state());

        assertThat(result).isEqualTo(ToolResult.failure("subagent_failed: execution timeout"));
    }

    @Test
    void wrapsRunnerExceptionAsSubagentFailure() {
        SubagentTool tool = new SubagentTool(prompt -> {
            throw new IllegalStateException("max_steps_exceeded");
        });

        ToolResult result = tool.execute(new ToolCall("spawn_subagent",
                singletonArgument("task_prompt", "调查日志")), state());

        assertThat(result).isEqualTo(ToolResult.failure("subagent_failed: max_steps_exceeded"));
    }

    @Test
    void marksToolAsSideEffectFalse() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("ok"));

        assertThat(tool.isSideEffect()).isFalse();
    }

    @Test
    void exposesTaskPromptOnlySchema() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("ok"));
        ToolDefinition definition = tool.definition();

        assertThat(definition.name()).isEqualTo("spawn_subagent");
        assertThat(definition.description()).contains("Subagent").contains("探索");
        assertThat(definition.parameters().get("required")).asString().isEqualTo("[task_prompt]");
        assertThat(definition.parameters().containsKey("properties")).isTrue();
        assertThat(definition.parameters().get("properties"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.map(String.class, Object.class))
                .containsOnlyKeys("task_prompt");
    }

    private Map<String, Object> singletonArgument(String key, Object value) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put(key, value);
        return arguments;
    }

    private AgentContext state() {
        return AgentContext.create(new Task("task-1", "test"));
    }
}
