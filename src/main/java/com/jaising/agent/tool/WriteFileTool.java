package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 写入工作区内文件的工具
 */
public final class WriteFileTool implements Tool {

    private final Path workDir;

    /**
     * 创建 write_file 工具
     */
    public WriteFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    /**
     * 返回工具名称。
     */
    @Override
    public String name() {
        return "write_file";
    }

    /**
     * 返回提供给模型的工具定义。
     */
    @Override
    public ToolDefinition definition() {
        Map<String, Object> pathProperty = new LinkedHashMap<String, Object>();
        pathProperty.put("type", "string");
        pathProperty.put("description", "Path relative to the workspace, for example src/main/java/App.java");

        Map<String, Object> contentProperty = new LinkedHashMap<String, Object>();
        contentProperty.put("type", "string");
        contentProperty.put("description", "Full file content to write");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProperty);
        properties.put("content", contentProperty);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "content"));

        return new ToolDefinition(name(), "Create or overwrite a file inside the workspace; "
                + "missing parent directories are created automatically", parameters);
    }

    /**
     * 执行文件写入。
     */
    @Override
    public ToolResult execute(ToolCall call, AgentState state) {
        Object rawPath = call.arguments().get("path");
        if (!(rawPath instanceof String) || ((String) rawPath).trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }

        Object rawContent = call.arguments().get("content");
        if (!(rawContent instanceof String)) {
            return ToolResult.failure("Missing required argument: content");
        }

        Path target = workDir.resolve((String) rawPath).normalize();
        if (!target.startsWith(workDir)) {
            return ToolResult.failure("Path escapes workspace: " + rawPath);
        }

        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, (String) rawContent);
        } catch (IOException ex) {
            return ToolResult.failure("Failed to write file: " + ex.getMessage());
        }

        return ToolResult.success("Wrote file: " + rawPath
                + " (" + ((String) rawContent).length() + " characters)");
    }
}
