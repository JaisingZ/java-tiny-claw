package io.github.tinyclaw.agent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * main loop 的工具调用载体。
 * 记录一次工具名和调用参数快照。
 */
public final class ToolCall {

    private final String toolName;
    private final Map<String, Object> arguments;

    /**
     * 创建工具调用。
     */
    public ToolCall(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(arguments));
    }

    /**
     * 获取工具名称。
     */
    public String toolName() {
        return toolName;
    }

    /**
     * 获取调用参数。
     */
    public Map<String, Object> arguments() {
        return arguments;
    }

    /**
     * 获取工具名称（兼容 get 风格调用）。
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * 获取调用参数（兼容 get 风格调用）。
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }

    /**
     * 判断工具调用是否等价。
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
     * 计算工具调用哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(toolName, arguments);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "ToolCall{"
                + "toolName='" + toolName + '\''
                + ", arguments=" + arguments
                + '}';
    }
}
