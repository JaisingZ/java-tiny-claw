package io.github.tinyclaw.agent.tool;

/**
 * 子智能体执行器。
 */
@FunctionalInterface
public interface SubagentRunner {

    ToolResult run(String taskPrompt);
}
