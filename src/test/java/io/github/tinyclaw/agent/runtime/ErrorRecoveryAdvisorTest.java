package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.ToolCall;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 错误自愈提示规则测试。
 */
class ErrorRecoveryAdvisorTest {

    private final ErrorRecoveryAdvisor advisor = new ErrorRecoveryAdvisor();

    /**
     * edit_file 找不到 old_text 时应提示先重新读取文件。
     */
    @Test
    void suggestsReadFileWhenEditOldTextIsMissing() {
        String observation = advisor.advise(call("edit_file", "path", "App.java"),
                "old_text not found");

        assertThat(observation).contains("Error executing edit_file: old_text not found");
        assertThat(observation).contains("[Recovery Hint]");
        assertThat(observation).contains("read_file");
        assertThat(observation).contains("exact current text");
    }

    /**
     * edit_file 多处匹配时应提示增加上下文。
     */
    @Test
    void suggestsMoreContextWhenEditMatchesMultiplePlaces() {
        String observation = advisor.advise(call("edit_file", "path", "App.java"),
                "old_text matched 2 places; provide more context");

        assertThat(observation).contains("Error executing edit_file");
        assertThat(observation).contains("[Recovery Hint]");
        assertThat(observation).contains("Add surrounding lines");
        assertThat(observation).contains("unique");
    }

    /**
     * 文件不存在时应提示先定位路径。
     */
    @Test
    void suggestsListingWorkspaceWhenFileIsMissing() {
        String readObservation = advisor.advise(call("read_file", "path", "Missing.java"),
                "File not found: Missing.java");
        String writeObservation = advisor.advise(call("write_file", "path", "src/Missing.java"),
                "File not found: src/Missing.java");

        assertThat(readObservation).contains("bash").contains("Get-ChildItem");
        assertThat(writeObservation).contains("bash").contains("Get-ChildItem");
    }

    /**
     * 未匹配的错误只保留执行错误，不伪造恢复建议。
     */
    @Test
    void keepsUnmatchedErrorWithoutRecoveryHint() {
        String observation = advisor.advise(call("bash", "command", "custom"),
                "domain-specific runtime failure");

        assertThat(observation).isEqualTo("Error executing bash: domain-specific runtime failure");
    }

    /**
     * 未知工具应提示检查工具名。
     */
    @Test
    void suggestsCheckingToolNameForUnknownTool() {
        String observation = advisor.advise(new ToolCall("missing_tool", Collections.<String, Object>emptyMap()),
                "Unknown tool: missing_tool");

        assertThat(observation).contains("[Recovery Hint]");
        assertThat(observation).contains("available tools");
        assertThat(observation).contains("tool name");
    }

    private static ToolCall call(String toolName, Object... arguments) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < arguments.length; i += 2) {
            values.put(String.valueOf(arguments[i]), arguments[i + 1]);
        }
        return new ToolCall(toolName, values);
    }
}
