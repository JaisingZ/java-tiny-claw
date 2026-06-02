package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolTest {

    @TempDir
    Path workDir;

    @Test
    void readsFileInsideWorkspace() throws Exception {
        Files.writeString(workDir.resolve("hello.txt"), "hello");
        ReadFileTool tool = new ReadFileTool(workDir);

        ToolResult result = tool.execute(call("hello.txt"), state());

        assertThat(result).isEqualTo(ToolResult.success("hello"));
    }

    @Test
    void rejectsPathTraversal() throws Exception {
        Path outside = Files.createTempFile("outside", ".txt");
        Files.writeString(outside, "secret");
        ReadFileTool tool = new ReadFileTool(workDir);

        ToolResult result = tool.execute(call("../" + outside.getFileName()), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Path escapes workspace");
    }

    @Test
    void returnsFailureWhenFileIsMissing() {
        ReadFileTool tool = new ReadFileTool(workDir);

        ToolResult result = tool.execute(call("missing.txt"), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("File not found");
    }

    @Test
    void truncatesLongFileOutput() throws Exception {
        Files.writeString(workDir.resolve("long.txt"), "a".repeat(8_100));
        ReadFileTool tool = new ReadFileTool(workDir);

        ToolResult result = tool.execute(call("long.txt"), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasSizeLessThan(8_100);
        assertThat(result.output()).contains("Output truncated");
    }

    @Test
    void exposesReadFileDefinition() {
        ReadFileTool tool = new ReadFileTool(workDir);

        ToolDefinition definition = tool.definition();

        assertThat(definition.name()).isEqualTo("read_file");
        assertThat(definition.description()).contains("Read");
        assertThat(definition.parameters()).containsKey("properties");
    }

    private ToolCall call(String path) {
        return new ToolCall("read_file", Collections.<String, Object>singletonMap("path", path));
    }

    private AgentContext state() {
        return AgentContext.create(new Task("task-1", "test"));
    }
}
