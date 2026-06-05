package io.github.tinyclaw.agent.context;

/**
 * System Prompt 组装器。
 * 负责把运行环境、阶段约束和工作区规范编译成模型可用的系统提示词。
 */
public interface PromptComposer {

    /**
     * 根据当前运行上下文组装 System Prompt。
     */
    String compose(PromptContext context);
}
