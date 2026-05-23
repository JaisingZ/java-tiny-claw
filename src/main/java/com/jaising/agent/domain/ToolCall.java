package com.jaising.agent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工具调用
 * 保存工具名和参数
 */
public final class ToolCall {

    private final String toolName;
    private final Map<String, Object> arguments;

    /**
     * 创建工具调用
     * 参数保持不可变
     */
    public ToolCall(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(arguments));
    }

    /**
     * 执行 toolName 操作。
     */
    public String toolName() {
        return toolName;
    }

    /**
     * 执行 arguments 操作。
     */
    public Map<String, Object> arguments() {
        return arguments;
    }

    /**
     * 读取 ToolName。
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 读取 Arguments。
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 比较对象是否相等。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolCall)) {
            return false;
        }
        ToolCall that = (ToolCall) other;
        return Objects.equals(toolName, that.toolName) && Objects.equals(arguments, that.arguments);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(toolName, arguments);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "ToolCall{"
                + "toolName='" + toolName + '\''
                + ", arguments=" + arguments
                + '}';
    }
}
