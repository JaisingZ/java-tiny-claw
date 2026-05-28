package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditFileToolTest {

    @TempDir
    Path workDir;

    @Test
    void replacesExactTextInsideExistingFile() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "hello old world");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "old", "new"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("hello.txt", "exact");
        assertThat(Files.readString(workDir.resolve("hello.txt"))).isEqualTo("hello new world");
    }

    @Test
    void replacesTextAfterNormalizingLineEndings() throws Exception {
        Files.writeString(workDir.resolve("code.txt"), "before\r\nold\r\ntext\r\nafter\r\n");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("code.txt", "old\ntext", "new\ntext"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("normalized_line_endings");
        assertThat(Files.readString(workDir.resolve("code.txt"))).isEqualTo("before\nnew\ntext\nafter\n");
    }

    @Test
    void replacesTextAfterTrimmingBlockBoundaryWhitespace() throws Exception {
        Files.writeString(workDir.resolve("code.txt"), "before\nold\ntext\nafter\n");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("code.txt", "\n\nold\ntext\n\n", "new"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("trimmed_block");
        assertThat(Files.readString(workDir.resolve("code.txt"))).isEqualTo("before\nnew\nafter\n");
    }

    @Test
    void replacesIndentedBlockWithLineByLineFuzzyMatch() throws Exception {
        Files.writeString(workDir.resolve("Server.java"),
                "class Server {\n"
                        + "    void run() {\n"
                        + "        if (user == null) {\n"
                        + "            return;\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("Server.java",
                "if (user == null) {\nreturn;\n}",
                "if (user == null) {\n    throw new IllegalStateException();\n}"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("line_by_line_trim");
        assertThat(Files.readString(workDir.resolve("Server.java"))).isEqualTo(
                "class Server {\n"
                        + "    void run() {\n"
                        + "if (user == null) {\n"
                        + "    throw new IllegalStateException();\n"
                        + "}\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    void deletesTextWhenNewTextIsEmpty() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "one\ntwo\nthree\n");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "two\n", ""), state());

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(workDir.resolve("hello.txt"))).isEqualTo("one\nthree\n");
    }

    @Test
    void rejectsPathTraversal() {
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("../outside.txt", "old", "new"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Path escapes workspace");
    }

    @Test
    void returnsFailureWhenOldTextIsMissing() {
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(new ToolCall("edit_file",
                arguments("path", "hello.txt", "new_text", "new")), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required argument: old_text");
    }

    @Test
    void returnsFailureWhenFileDoesNotExist() {
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("missing.txt", "old", "new"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("File not found");
    }

    @Test
    void returnsFailureWhenTextIsNotFound() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "hello");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "missing", "new"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("old_text not found");
    }

    @Test
    void returnsFailureWhenTextMatchesMultiplePlaces() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "same\nsame\n");
        EditFileTool tool = new EditFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "same", "new"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("matched 2 places");
        assertThat(Files.readString(workDir.resolve("hello.txt"))).isEqualTo("same\nsame\n");
    }

    @Test
    void exposesEditFileDefinition() {
        EditFileTool tool = new EditFileTool(workDir);

        ToolDefinition definition = tool.definition();

        assertThat(definition.name()).isEqualTo("edit_file");
        assertThat(definition.parameters().toString()).contains("path", "old_text", "new_text", "required");
    }

    private ToolCall call(String path, String oldText, String newText) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("old_text", oldText);
        arguments.put("new_text", newText);
        return new ToolCall("edit_file", arguments);
    }

    private Map<String, Object> arguments(String firstKey, Object firstValue,
            String secondKey, Object secondValue) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put(firstKey, firstValue);
        arguments.put(secondKey, secondValue);
        return arguments;
    }

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
    }
}
