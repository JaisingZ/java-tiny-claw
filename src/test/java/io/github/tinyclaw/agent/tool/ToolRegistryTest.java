package io.github.tinyclaw.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolRegistryTest {

    @TempDir
    Path workDir;

    @Test
    void exposesRegisteredToolDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadOnlyTool());

        List<ToolDefinition> definitions = registry.definitions();

        assertThat(definitions).containsExactly(new ToolDefinition(
                "read_only",
                "只读工具",
                Collections.<String, Object>singletonMap("type", "object")));
    }

    @Test
    void executesRegisteredTool() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadOnlyTool());

        ToolResult result = registry.execute(new ToolCall("read_only",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.success("ok"));
    }

    @Test
    void returnsFailureWhenToolIsUnknown() {
        ToolRegistry registry = new ToolRegistry();

        ToolResult result = registry.execute(new ToolCall("missing",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("Unknown tool: missing"));
    }

    @Test
    void wrapsToolExceptionAsFailure() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new BrokenTool());

        ToolResult result = registry.execute(new ToolCall("broken",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("tool_error: boom"));
    }

    @Test
    void runsMiddlewaresBeforeToolInOrder() {
        StringBuilder events = new StringBuilder();
        ToolRegistry registry = new ToolRegistry()
                .register(new ReadOnlyTool())
                .use((call, context, next) -> {
                    events.append("a>");
                    ToolResult result = next.execute(call, context);
                    events.append("<a");
                    return result;
                })
                .use((call, context, next) -> {
                    events.append("b>");
                    ToolResult result = next.execute(call, context);
                    events.append("<b");
                    return result;
                });

        ToolResult result = registry.execute(new ToolCall("read_only",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.success("ok"));
        assertThat(events).hasToString("a>b><b<a");
    }

    @Test
    void middlewareCanBlockToolExecution() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry()
                .register(new CountingTool(executions))
                .use((call, context, next) -> ToolResult.failure("blocked"));

        ToolResult result = registry.execute(new ToolCall("count",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("blocked"));
        assertThat(executions).hasValue(0);
    }

    @Test
    void wrapsMiddlewareExceptionAsFailure() {
        ToolRegistry registry = new ToolRegistry()
                .register(new ReadOnlyTool())
                .use((call, context, next) -> {
                    throw new IllegalStateException("approval unavailable");
                });

        ToolResult result = registry.execute(new ToolCall("read_only",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("middleware_error: approval unavailable"));
    }

    @Test
    void toolExceptionBehindMiddlewareStillReportsToolError() {
        ToolRegistry registry = new ToolRegistry()
                .register(new BrokenTool())
                .use((call, context, next) -> next.execute(call, context));

        ToolResult result = registry.execute(new ToolCall("broken",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result).isEqualTo(ToolResult.failure("tool_error: boom"));
    }

    @Test
    void executesWriteAndBashTools() {
        ToolRegistry registry = new ToolRegistry()
                .register(new WriteFileTool(workDir))
                .register(new BashTool(workDir, Duration.ofSeconds(5), 8_000));

        ToolResult writeResult = registry.execute(new ToolCall("write_file",
                writeArguments("hello.txt", "hello")), state());
        ToolResult bashResult = registry.execute(new ToolCall("bash",
                Collections.<String, Object>singletonMap("command", printFileCommand("hello.txt"))), state());

        assertThat(writeResult.success()).isTrue();
        assertThat(bashResult.success()).isTrue();
        assertThat(bashResult.output()).contains("hello");
    }

    @Test
    void executesEditFileTool() throws Exception {
        java.nio.file.Files.writeString(workDir.resolve("hello.txt"), "hello old world");
        ToolRegistry registry = new ToolRegistry()
                .register(new EditFileTool(workDir));

        ToolResult result = registry.execute(new ToolCall("edit_file",
                editArguments("hello.txt", "old", "new")), state());

        assertThat(result.success()).isTrue();
        assertThat(java.nio.file.Files.readString(workDir.resolve("hello.txt"))).isEqualTo("hello new world");
    }

    private AgentContext state() {
        return AgentContext.create(new Task("task-1", "test"));
    }

    private Map<String, Object> writeArguments(String path, String content) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("content", content);
        return arguments;
    }

    private Map<String, Object> editArguments(String path, String oldText, String newText) {
        Map<String, Object> arguments = new LinkedHashMap<String, Object>();
        arguments.put("path", path);
        arguments.put("old_text", oldText);
        arguments.put("new_text", newText);
        return arguments;
    }

    private String printFileCommand(String path) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "Get-Content '" + path + "'";
        }
        return "cat '" + path + "'";
    }

    private static final class ReadOnlyTool implements Tool {
        @Override
        public String name() {
            return "read_only";
        }

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(name(), "只读工具",
                    Collections.<String, Object>singletonMap("type", "object"));
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.success("ok");
        }

        @Override
        public boolean isSideEffect() {
            return false;
        }
    }

    private static final class BrokenTool implements Tool {
        @Override
        public String name() {
            return "broken";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            throw new RuntimeException("boom");
        }
    }

    private static final class CountingTool implements Tool {
        private final AtomicInteger executions;

        private CountingTool(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public String name() {
            return "count";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            executions.incrementAndGet();
            return ToolResult.success("counted");
        }
    }
}
