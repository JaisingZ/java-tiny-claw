package io.github.tinyclaw.agent.tool;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.util.Collections;

/**
 * Main Loop 可调用的工具接口。
 * 负责执行具体动作并返回工具观测结果。
 */
public interface Tool {
    /**
     * 返回工具名称。
     */
    String name();

    /**
     * 返回提供给模型的工具定义。
     */
    default ToolDefinition definition() {
        return new ToolDefinition(name(), name(),
                Collections.<String, Object>singletonMap("type", "object"));
    }

    /**
     * 在当前上下文中执行工具调用。
     */
    ToolResult execute(ToolCall call, AgentContext context);

    /**
     * 标识工具是否具有副作用（写操作）。
     * 默认为 true（保守策略），只读工具（如读取文件）应重写为 false。
     */
    default boolean isSideEffect() {
        return true;
    }
}
