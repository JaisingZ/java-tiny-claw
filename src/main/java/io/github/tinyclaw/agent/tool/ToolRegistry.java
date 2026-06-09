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
    private final List<ToolMiddleware> middlewares = new ArrayList<ToolMiddleware>();

    /**
     * 注册工具并返回当前注册表，便于链式挂载。
     */
    public ToolRegistry register(Tool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    /**
     * 挂载工具执行中间件并返回当前注册表，便于链式组装。
     */
    public ToolRegistry use(ToolMiddleware middleware) {
        middlewares.add(middleware);
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
            return executeWithMiddleware(0, tool, call, context);
        } catch (ToolMiddlewareException ex) {
            return ToolResult.failure("middleware_error: " + ex.getMessage());
        } catch (ToolExecutionException ex) {
            return ToolResult.failure("tool_error: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return ToolResult.failure("tool_error: " + ex.getMessage());
        }
    }

    private ToolResult executeWithMiddleware(int index, Tool tool, ToolCall call, AgentContext context) {
        if (index >= middlewares.size()) {
            try {
                return tool.execute(call, context);
            } catch (RuntimeException ex) {
                throw new ToolExecutionException(ex.getMessage(), ex);
            }
        }
        ToolMiddleware middleware = middlewares.get(index);
        try {
            return middleware.execute(call, context,
                    (nextCall, nextContext) -> executeWithMiddleware(index + 1, tool, nextCall, nextContext));
        } catch (RuntimeException ex) {
            if (ex instanceof ToolExecutionException || ex instanceof ToolMiddlewareException) {
                throw ex;
            }
            throw new ToolMiddlewareException(ex.getMessage(), ex);
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

    /**
     * 工具执行中间件。
     */
    @FunctionalInterface
    public interface ToolMiddleware {

        ToolResult execute(ToolCall call, AgentContext context, ToolExecution next);
    }

    /**
     * 中间件放行后的后续执行节点。
     */
    @FunctionalInterface
    public interface ToolExecution {

        ToolResult execute(ToolCall call, AgentContext context);
    }

    private static final class ToolMiddlewareException extends RuntimeException {

        private ToolMiddlewareException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ToolExecutionException extends RuntimeException {

        private ToolExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
