package io.github.tinyclaw.agent.tool;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Loop 的工具注册表。
 * 负责按名称注册、查找、分发工具调用。
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<String, Tool>();

    /**
     * 注册工具并返回当前注册表，便于链式挂载。
     */
    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * 按名称查找工具，找不到时抛出异常。
     */
    public Tool require(String toolName) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return tool;
    }

    /**
     * 路由并执行工具调用，未知工具和工具异常会包装成失败结果。
     */
    public ToolResult execute(ToolCall call, AgentContext context) {
        Tool tool;
        try {
            tool = require(call.toolName());
        } catch (RuntimeException ex) {
            return ToolResult.failure(ex.getMessage());
        }

        try {
            return tool.execute(call, context);
        } catch (RuntimeException ex) {
            return ToolResult.failure("tool_error: " + ex.getMessage());
        }
    }

    /**
     * 返回当前工具映射的只读快照。
     */
    public Map<String, Tool> snapshot() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * 返回提供给模型的工具定义快照。
     */
    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.definition());
        }
        return Collections.unmodifiableList(definitions);
    }
}
