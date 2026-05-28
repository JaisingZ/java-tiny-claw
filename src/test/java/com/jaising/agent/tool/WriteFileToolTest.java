package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WriteFileToolTest {

    @TempDir
    Path workDir;

    @Test
    void writesNewFileInsideWorkspace() throws Exception {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "hello"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("hello.txt", "5");
        assertThat(Files.readString(workDir.resolve("hello.txt"))).isEqualTo("hello");
    }

    @Test
    void writesUtf8TextWithoutNulBytes() throws Exception {
        WriteFileTool tool = new WriteFileTool(workDir);
        String source = "public class Hello {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello，Java！\");\n"
                + "    }\n"
                + "}\n";

        ToolResult result = tool.execute(call("Hello.java", source), state());

        byte[] bytes = Files.readAllBytes(workDir.resolve("Hello.java"));
        assertThat(result.success()).isTrue();
        assertThat(bytes).doesNotContain((byte) 0);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(source);
    }

    @Test
    void rejectsContentWithNulBytes() {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(call("Hello.java", "public\0 class Hello {}"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Content contains NUL bytes");
        assertThat(Files.exists(workDir.resolve("Hello.java"))).isFalse();
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(call("nested/dir/hello.txt", "hello"), state());

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(workDir.resolve("nested/dir/hello.txt"))).isEqualTo("hello");
    }

    @Test
    void overwritesExistingFile() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "old");
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt", "new"), state());

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(workDir.resolve("hello.txt"))).isEqualTo("new");
    }

    @Test
    void rejectsPathTraversal() {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(call("../outside.txt", "secret"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Path escapes workspace");
    }

    @Test
    void returnsFailureWhenPathIsMissing() {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(new ToolCall("write_file",
                singletonArguments("content", "hello")), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required argument: path");
    }

    @Test
    void returnsFailureWhenContentIsMissing() {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolResult result = tool.execute(new ToolCall("write_file",
                singletonArguments("path", "hello.txt")), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required argument: content");
    }

    @Test
    void exposesWriteFileDefinition() {
        WriteFileTool tool = new WriteFileTool(workDir);

        ToolDefinition definition = tool.definition();

        assertThat(definition.name()).isEqualTo("write_file");
        assertThat(definition.description()).contains("parent directories", "UTF-8");
        assertThat(definition.parameters().toString()).contains("path", "content", "required", "UTF-8");
    }

    private ToolCall call(String path, String content) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("content", content);
        return new ToolCall("write_file", arguments);
    }

    private Map<String, Object> singletonArguments(String key, Object value) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put(key, value);
        return arguments;
    }

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
    }
}
