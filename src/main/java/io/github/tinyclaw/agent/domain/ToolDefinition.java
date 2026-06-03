package io.github.tinyclaw.agent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * main loop 使用的工具定义。
 * 约束模型可见的工具名、描述与参数结构。
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    /**
     * 创建工具定义。
     */
    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(parameters));
    }

    /**
     * 获取工具名称。
     */
    public String name() {
        return name;
    }

    /**
     * 获取工具描述。
     */
    public String description() {
        return description;
    }

    /**
     * 获取参数定义。
     */
    public Map<String, Object> parameters() {
        return parameters;
    }

    /**
     * 获取工具名称（兼容 get 风格调用）。
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述（兼容 get 风格调用）。
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取参数定义（兼容 get 风格调用）。
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * 判断工具定义是否等价。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolDefinition)) {
            return false;
        }
        ToolDefinition that = (ToolDefinition) other;
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(parameters, that.parameters);
    }

    /**
     * 计算工具定义哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, description, parameters);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "ToolDefinition{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", parameters=" + parameters
                + '}';
    }
}
