package com.jaising.agent.tool;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 局部编辑工作区内现有文件的工具。
 */
public final class EditFileTool implements Tool {

    private final Path workDir;

    /**
     * 创建限定在指定工作区内的 edit_file 工具。
     */
    public EditFileTool(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    /**
     * 返回工具名称。
     */
    @Override
    public String name() {
        return "edit_file";
    }

    /**
     * 返回提供给模型的工具定义。
     */
    @Override
    public ToolDefinition definition() {
        Map<String, Object> pathProperty = new LinkedHashMap<String, Object>();
        pathProperty.put("type", "string");
        pathProperty.put("description", "Path relative to the workspace, for example src/main/java/App.java");

        Map<String, Object> oldTextProperty = new LinkedHashMap<String, Object>();
        oldTextProperty.put("type", "string");
        oldTextProperty.put("description", "Existing text to replace. Include enough surrounding context to match once.");

        Map<String, Object> newTextProperty = new LinkedHashMap<String, Object>();
        newTextProperty.put("type", "string");
        newTextProperty.put("description", "Replacement text. Empty string deletes the matched text.");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProperty);
        properties.put("old_text", oldTextProperty);
        properties.put("new_text", newTextProperty);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "old_text", "new_text"));

        return new ToolDefinition(name(), "Edit an existing file by replacing one unique text block", parameters);
    }

    /**
     * 执行唯一文本块替换。
     */
    @Override
    public ToolResult execute(ToolCall call, AgentContext context) {
        Object rawPath = call.arguments().get("path");
        if (!(rawPath instanceof String) || ((String) rawPath).trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }

        Object rawOldText = call.arguments().get("old_text");
        if (!(rawOldText instanceof String) || ((String) rawOldText).trim().isEmpty()) {
            return ToolResult.failure("Missing required argument: old_text");
        }

        Object rawNewText = call.arguments().get("new_text");
        if (!(rawNewText instanceof String)) {
            return ToolResult.failure("Missing required argument: new_text");
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

        Replacement replacement = replace(content, (String) rawOldText, (String) rawNewText);
        if (!replacement.success()) {
            return ToolResult.failure(replacement.errorMessage());
        }

        try {
            Files.writeString(target, replacement.content());
        } catch (IOException ex) {
            return ToolResult.failure("Failed to write file: " + ex.getMessage());
        }

        return ToolResult.success("Edited file: " + rawPath + " (strategy=" + replacement.strategy() + ")");
    }

    private Replacement replace(String content, String oldText, String newText) {
        Replacement exact = replaceUnique(content, oldText, newText, "exact");
        if (exact.terminal()) {
            return exact;
        }

        String normalizedContent = normalizeLineEndings(content);
        String normalizedOldText = normalizeLineEndings(oldText);
        String normalizedNewText = normalizeLineEndings(newText);
        Replacement normalized = replaceUnique(normalizedContent, normalizedOldText,
                normalizedNewText, "normalized_line_endings");
        if (normalized.terminal()) {
            return normalized;
        }

        String trimmedOldText = normalizedOldText.trim();
        Replacement trimmed = replaceUnique(normalizedContent, trimmedOldText,
                normalizedNewText, "trimmed_block");
        if (trimmed.terminal()) {
            return trimmed;
        }

        return lineByLineReplace(normalizedContent, trimmedOldText, normalizedNewText);
    }

    private Replacement replaceUnique(String content, String oldText, String newText, String strategy) {
        if (oldText.isEmpty()) {
            return Replacement.notFound();
        }

        int count = countOccurrences(content, oldText);
        if (count == 1) {
            return Replacement.success(content.replace(oldText, newText), strategy);
        }
        if (count > 1) {
            return Replacement.failure("old_text matched " + count + " places; provide more context");
        }
        return Replacement.notFound();
    }

    private Replacement lineByLineReplace(String content, String oldText, String newText) {
        if (oldText.isEmpty()) {
            return Replacement.failure("old_text not found");
        }

        String[] contentLines = content.split("\n", -1);
        String[] oldLines = oldText.split("\n", -1);
        if (oldLines.length == 0 || contentLines.length < oldLines.length) {
            return Replacement.failure("old_text not found");
        }

        for (int i = 0; i < oldLines.length; i++) {
            oldLines[i] = oldLines[i].trim();
        }

        int matchCount = 0;
        int matchStart = -1;
        int matchEnd = -1;
        for (int i = 0; i <= contentLines.length - oldLines.length; i++) {
            if (matchesTrimmedWindow(contentLines, oldLines, i)) {
                matchCount++;
                matchStart = i;
                matchEnd = i + oldLines.length;
            }
        }

        if (matchCount == 0) {
            return Replacement.failure("old_text not found");
        }
        if (matchCount > 1) {
            return Replacement.failure("old_text fuzzy matched " + matchCount + " places; provide more context");
        }

        List<String> newContentLines = new ArrayList<String>();
        newContentLines.addAll(Arrays.asList(contentLines).subList(0, matchStart));
        newContentLines.addAll(Arrays.asList(newText.split("\n", -1)));
        newContentLines.addAll(Arrays.asList(contentLines).subList(matchEnd, contentLines.length));
        return Replacement.success(String.join("\n", newContentLines), "line_by_line_trim");
    }

    private boolean matchesTrimmedWindow(String[] contentLines, String[] oldLines, int start) {
        for (int i = 0; i < oldLines.length; i++) {
            if (!contentLines[start + i].trim().equals(oldLines[i])) {
                return false;
            }
        }
        return true;
    }

    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }

    private int countOccurrences(String content, String text) {
        int count = 0;
        int index = 0;
        while (index <= content.length()) {
            int found = content.indexOf(text, index);
            if (found < 0) {
                return count;
            }
            count++;
            index = found + text.length();
        }
        return count;
    }

    private static final class Replacement {
        private final boolean success;
        private final boolean terminal;
        private final String content;
        private final String strategy;
        private final String errorMessage;

        private Replacement(boolean success, boolean terminal, String content,
                String strategy, String errorMessage) {
            this.success = success;
            this.terminal = terminal;
            this.content = content;
            this.strategy = strategy;
            this.errorMessage = errorMessage;
        }

        static Replacement success(String content, String strategy) {
            return new Replacement(true, true, content, strategy, null);
        }

        static Replacement failure(String errorMessage) {
            return new Replacement(false, true, null, null, errorMessage);
        }

        static Replacement notFound() {
            return new Replacement(false, false, null, null, null);
        }

        boolean success() {
            return success;
        }

        boolean terminal() {
            return terminal;
        }

        String content() {
            return content;
        }

        String strategy() {
            return strategy;
        }

        String errorMessage() {
            return errorMessage;
        }
    }

    /**
     * 明确标记具有副作用。
     */
    @Override
    public boolean isSideEffect() {
        return true;
    }
}
