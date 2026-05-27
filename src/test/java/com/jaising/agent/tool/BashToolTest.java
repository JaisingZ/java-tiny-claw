package com.jaising.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashToolTest {

    @TempDir
    Path workDir;

    @Test
    void executesCommandAndReturnsOutput() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 8_000);

        ToolResult result = tool.execute(call(commandThatPrints("hello")), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("hello");
    }

    @Test
    void executesCommandInsideWorkspace() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 8_000);

        ToolResult result = tool.execute(call(commandThatPrintsWorkingDirectory()), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(workDir.toAbsolutePath().normalize().toString());
    }

    @Test
    void returnsNonZeroExitAsObservation() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 8_000);

        ToolResult result = tool.execute(call(commandThatFailsWithOutput()), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Exit code: 7", "boom");
    }

    @Test
    void returnsMessageWhenCommandHasNoOutput() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 8_000);

        ToolResult result = tool.execute(call(commandWithNoOutput()), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Command executed successfully with no output");
    }

    @Test
    void truncatesLongOutput() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 80);

        ToolResult result = tool.execute(call(commandThatPrintsLongOutput()), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).hasSizeLessThan(200);
        assertThat(result.output()).contains("Output truncated");
    }

    @Test
    void terminatesCommandOnTimeout() {
        BashTool tool = new BashTool(workDir, Duration.ofMillis(200), 8_000);

        ToolResult result = tool.execute(call(commandThatSleeps()), state());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Command timed out");
    }

    @Test
    void returnsFailureWhenCommandIsMissing() {
        BashTool tool = new BashTool(workDir, Duration.ofSeconds(5), 8_000);

        ToolResult result = tool.execute(new ToolCall("bash",
                Collections.<String, Object>emptyMap()), state());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Missing required argument: command");
    }

    @Test
    void exposesBashDefinition() {
        BashTool tool = new BashTool(workDir);

        ToolDefinition definition = tool.definition();

        assertThat(definition.name()).isEqualTo("bash");
        assertThat(definition.parameters().toString()).contains("command", "required");
    }

    private ToolCall call(String command) {
        return new ToolCall("bash", Collections.<String, Object>singletonMap("command", command));
    }

    private AgentState state() {
        return AgentState.create(new Task("task-1", "test"));
    }

    private String commandThatPrints(String value) {
        if (isWindows()) {
            return "Write-Output '" + value + "'";
        }
        return "printf '" + value + "'";
    }

    private String commandThatPrintsWorkingDirectory() {
        if (isWindows()) {
            return "(Get-Location).Path";
        }
        return "pwd";
    }

    private String commandThatFailsWithOutput() {
        if (isWindows()) {
            return "Write-Output 'boom'; exit 7";
        }
        return "echo boom; exit 7";
    }

    private String commandWithNoOutput() {
        if (isWindows()) {
            return "New-Item -ItemType File -Name empty.txt | Out-Null";
        }
        return "touch empty.txt";
    }

    private String commandThatPrintsLongOutput() {
        if (isWindows()) {
            return "'a' * 200";
        }
        return "printf '%*s' 200 '' | tr ' ' a";
    }

    private String commandThatSleeps() {
        if (isWindows()) {
            return "Start-Sleep -Seconds 2";
        }
        return "sleep 2";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
