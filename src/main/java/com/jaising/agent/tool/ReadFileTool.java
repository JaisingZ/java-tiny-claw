package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取工作区内文件的工具。
 */
public final class ReadFileTool implements Tool {

    private static final int MAX_OUTPUT_CHARS = 8_000;

    private final Path workDir;

    /**
     * 创建限定在指定工作区内的 read_file 工具。
     */
    public ReadFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    /**
     * 返回工具名称。
     */
    @Override
    public String name() {
        return "read_file";
    }

    /**
     * 返回提供给模型的工具定义。
     */
    @Override
    public ToolDefinition definition() {
        Map<String, Object> pathProperty = new LinkedHashMap<String, Object>();
        pathProperty.put("type", "string");
        pathProperty.put("description", "Path relative to the workspace, for example src/main/java/App.java");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProperty);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));

        return new ToolDefinition(name(), "Read a file inside the workspace", parameters);
    }

    /**
     * 执行文件读取并返回文本内容。
     */
    @Override
    public ToolResult execute(ToolCall call, AgentContext context) {
        Object rawPath = call.arguments().get("path");
        if (!(rawPath instanceof String) || ((String) rawPath).trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }

        Path target = workDir.resolve((String) rawPath).normalize();
        if (!target.startsWith(workDir)) {
            return ToolResult.failure("Path escapes workspace: " + rawPath);
        }

        String content;
        try {
            content = Files.readString(target);
        } catch (NoSuchFileException ex) {
            return ToolResult.failure("File not found: " + rawPath);
        } catch (IOException ex) {
            return ToolResult.failure("Failed to read file: " + ex.getMessage());
        }

        if (content.length() <= MAX_OUTPUT_CHARS) {
            return ToolResult.success(content);
        }
        return ToolResult.success(content.substring(0, MAX_OUTPUT_CHARS)
                + "\n\n[Output truncated to " + MAX_OUTPUT_CHARS + " characters]");
    }

    /**
     * 显式标记为只读。
     */
    @Override
    public boolean isSideEffect() {
        return false;
    }
}
