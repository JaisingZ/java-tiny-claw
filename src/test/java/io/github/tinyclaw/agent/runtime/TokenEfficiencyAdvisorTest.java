package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TokenEfficiencyAdvisor 关键分支测试。
 */
class TokenEfficiencyAdvisorTest {

    @Test
    void marksPendingAfterValidationWriteOrEditAndClearsPassedOnSuccessValidation() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-pending", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall bashPass = new ToolCall("bash", Map.of("command", "mvn -q test"));

        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("just modified")
                .contains("validation");
        assertThat(advisor.validationPending()).isTrue();
        assertThat(advisor.validationPassed()).isFalse();

        assertThat(advisor.afterToolCall(context, bashPass, ToolResult.success("result=ok")))
                .isNull();
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isTrue();
    }

    @Test
    void clearsPendingAfterBashValidationFailure() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-failed", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall bashFail = new ToolCall("bash", Map.of("command", "mvn -q test"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");
        assertThat(advisor.validationPending()).isTrue();

        String reminder = advisor.afterToolCall(context, bashFail, ToolResult.success("exitCode=1"));

        assertThat(reminder).contains("[SYSTEM REMINDER]");
        assertThat(reminder).doesNotContain("Priority: keep changes minimal");
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();
    }

    @Test
    void marksPassedForValidationCommandEvenWithoutSuccessMarker() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-compile", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall javac = new ToolCall("bash", Map.of("command", "javac target/App.java"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");

        String reminder = advisor.afterToolCall(context, javac,
                ToolResult.success("Command executed successfully with no output"));

        assertThat(reminder).isNull();
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isTrue();
    }

    @Test
    void doesNotMarkPassedForValidationCommandWithAmbiguousOutput() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-ambiguous", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall javac = new ToolCall("bash", Map.of("command", "javac target/App.java"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");

        String reminder = advisor.afterToolCall(context, javac,
                ToolResult.success("Compilation failed unexpectedly"));

        assertThat(reminder).isNull();
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();
    }

    @Test
    void doesNotMarkPassedForNonValidationBashCommandWithoutSuccessMarker() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-list", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall listTestDir = new ToolCall("bash", Map.of("command", "ls test"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");

        String reminder = advisor.afterToolCall(context, listTestDir, ToolResult.success("test"));

        assertThat(reminder).isNull();
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();
    }

    @Test
    void clearsPendingAfterBashToolFailure() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-tool-failure", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall bash = new ToolCall("bash", Map.of("command", "validation.ps1"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");

        String reminder = advisor.afterToolCall(context, bash, ToolResult.failure("middleware_error: denied"));

        assertThat(reminder).isNull();
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();
    }

    @Test
    void clearsPendingAfterBashTimeoutOutput() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-timeout", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall bash = new ToolCall("bash", Map.of("command", "validation.ps1"));

        assertThat(advisor.afterToolCall(context, writeFile, ToolResult.success("edited")))
                .contains("validation");

        String reminder = advisor.afterToolCall(context, bash,
                ToolResult.success("[Command timed out after 30000 ms and was terminated]"));

        assertThat(reminder).contains("[SYSTEM REMINDER]");
        assertThat(advisor.validationPending()).isFalse();
        assertThat(advisor.validationPassed()).isFalse();
    }

    @Test
    void remindsOnBashJavacEncodingFailureWhenValidationTask() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-encoding", "修复代码后执行 validation.ps1 验证"));
        ToolCall bashCall = new ToolCall("bash", Map.of("command", "mvn -q test"));

        String reminder = advisor.afterToolCall(context, bashCall,
                ToolResult.success("exitCode=1\n编码GBK的不可映射字符"));

        assertThat(reminder)
                .contains("[SYSTEM REMINDER]")
                .contains("Priority: keep changes minimal and fix only current file");
    }

    @Test
    void doesNotRemindOnReadFileOutputEvenWithEncodingMarker() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-read-file", "读取文件"));
        ToolCall readFile = new ToolCall("read_file", Map.of("path", "README.md"));

        String reminder = advisor.afterToolCall(context, readFile,
                ToolResult.success("编码GBK的不可映射字符"));

        assertThat(reminder).isNull();
    }

    @Test
    void remindsToUseWriteFileAfterReadingSourceForValidationTask() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-source-read", "修复代码后执行 validation.ps1 验证"));
        ToolCall readFile = new ToolCall("read_file", Map.of("path", "src/App.java"));

        String reminder = advisor.afterToolCall(context, readFile, ToolResult.success("class App {}"));

        assertThat(reminder)
                .contains("[SYSTEM REMINDER]")
                .contains("write_file")
                .contains("edit_file")
                .contains("old_text");
        assertThat(advisor.afterToolCall(context, readFile, ToolResult.success("class App {}")))
                .isNull();
    }

    @Test
    void writeOrEditOutputDoesNotTriggerEncodingSpecificReminder() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-write-edit", "修复代码后执行 validation.ps1 验证"));
        ToolCall writeFile = new ToolCall("write_file", Map.of("path", "src/App.java"));
        ToolCall editFile = new ToolCall("edit_file", Map.of("path", "src/App.java"));

        assertThat(advisor.afterToolCall(context, writeFile,
                ToolResult.success("编码GBK的不可映射字符")))
                .contains("file was just modified")
                .doesNotContain("encoding failure");
        assertThat(advisor.afterToolCall(context, editFile,
                ToolResult.success("编码GBK的不可映射字符")))
                .contains("file was just modified")
                .doesNotContain("encoding failure");
    }

    @Test
    void compactsLargeEncodingValidationFailureToConciseObservation() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-validation", "修复后执行 validation.ps1 验证"));
        ToolCall bash = new ToolCall("bash", Map.of("command", "mvn -q test"));
        StringBuilder output = new StringBuilder();
        output.append("exitCode=1\njavac: error: unmappable character for encoding\n");
        for (int i = 0; i < 120; i++) {
            output.append("src/Main.java:").append(i).append(": error: 非 ASCII 注释\n");
            output.append("x".repeat(80)).append('\n');
        }

        String compacted = advisor.compactValidationFailureObservation(context, bash,
                ToolResult.success(output.toString()));

        assertThat(compacted).contains("[SYSTEM REMINDER]");
        assertThat(compacted).contains("remove non-ASCII comments/strings");
        assertThat(compacted).contains("javac encoding failure");
        assertThat(compacted.length()).isLessThan(600 + 40);
        assertThat(compacted).doesNotContain("x".repeat(40));
    }

    @Test
    void compactsExitCodeFailureValidationOutputToConciseObservation() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-validation", "修复后执行 validation.ps1 验证"));
        ToolCall bash = new ToolCall("bash", Map.of("command", "mvn -q test"));
        String output = "exitCode=1";

        String compacted = advisor.compactValidationFailureObservation(context, bash,
                ToolResult.success(output));

        assertThat(compacted).contains("[SYSTEM REMINDER]");
        assertThat(compacted).contains("Fix action:");
    }

    @Test
    void compactsTimeoutValidationOutputToConciseObservation() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-validation", "修复后执行 validation.ps1 验证"));
        ToolCall bash = new ToolCall("bash", Map.of("command", "mvn -q test"));
        String output = "[Command timed out after 30000 ms and was terminated]";

        String compacted = advisor.compactValidationFailureObservation(context, bash,
                ToolResult.success(output));

        assertThat(compacted).contains("[SYSTEM REMINDER]");
        assertThat(compacted).contains("Fix action:");
        assertThat(compacted).contains("timed out");
    }

    @Test
    void doesNotRemindOnSubagentOutputEvenWithEncodingMarker() {
        TokenEfficiencyAdvisor advisor = new TokenEfficiencyAdvisor();
        AgentContext context = AgentContext.create(new Task("task-subagent", "修复代码后执行 validation.ps1 验证"));
        ToolCall subagent = new ToolCall("spawn_subagent", Map.of("prompt", "inspect validation failure"));

        String reminder = advisor.afterToolCall(context, subagent,
                ToolResult.success("javac: error: unmappable character for encoding"));

        assertThat(reminder).isNull();
    }
}
