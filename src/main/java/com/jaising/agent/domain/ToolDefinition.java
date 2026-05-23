package com.jaising.agent.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 工具定义
 * 描述模型可调用工具的名称和参数结构
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final Map<String, Object> parameters;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(parameters));
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(name, description, parameters);
    }

    @Override
    public String toString() {
        return "ToolDefinition{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", parameters=" + parameters
                + '}';
    }
}
