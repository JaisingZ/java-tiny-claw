package com.jaising.agent.tool;

import com.jaising.agent.domain.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * 负责命名 注册和查找
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
            /**
             * 执行 IllegalArgumentException 操作。
             */
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return tool;
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
