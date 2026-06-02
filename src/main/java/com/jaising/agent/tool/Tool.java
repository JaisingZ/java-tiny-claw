package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.util.Collections;

/**
 * 工具接口
 * 负责执行具体动作并返回结果
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
     * 执行工具调用。
     */
    ToolResult execute(ToolCall call, AgentContext state);

    /**
     * 标识工具是否具有副作用（写操作）。
     * 默认为 true（保守策略），只读工具（如读取文件）应重写为 false。
     */
    default boolean isSideEffect() {
        return true;
    }
}
