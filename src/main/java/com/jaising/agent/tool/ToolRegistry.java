package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * 负责命名 注册 查找和分发
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<String, Tool>();

    /**
     * 注册工具
     */
    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * 按名称查找工具
     * 找不到就直接失败
     */
    public Tool require(String toolName) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return tool;
    }

    /**
     * 路由并执行工具调用
     * 未知工具和工具异常都包装成结构化失败
     */
    public ToolResult execute(ToolCall call, AgentContext state) {
        Tool tool;
        try {
            tool = require(call.toolName());
        } catch (RuntimeException ex) {
            return ToolResult.failure(ex.getMessage());
        }

        try {
            return tool.execute(call, state);
        } catch (RuntimeException ex) {
            return ToolResult.failure("tool_error: " + ex.getMessage());
        }
    }

    /**
     * 输出快照
     */
    public Map<String, Tool> snapshot() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * 输出工具定义快照
     */
    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<ToolDefinition>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.definition());
        }
        return Collections.unmodifiableList(definitions);
    }
}
