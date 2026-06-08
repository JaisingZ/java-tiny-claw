package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * System Reminder 死循环防呆测试。
 */
class SystemReminderInjectorTest {

    private final SystemReminderInjector injector = new SystemReminderInjector();

    /**
     * 同一失败调用连续第三次才触发提醒。
     */
    @Test
    void remindsOnThirdRepeatedFailedCall() {
        ToolCall call = call("read_file", "path", "missing.txt");

        assertThat(injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"))).isNull();
        assertThat(injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"))).isNull();

        String reminder = injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"));

        assertThat(reminder)
                .contains("[SYSTEM REMINDER]")
                .contains("read_file")
                .contains("3 consecutive times")
                .contains("change strategy");
    }

    /**
     * 成功调用清空无效调用计数。
     */
    @Test
    void successClearsRepeatedFailureCounts() {
        ToolCall call = call("read_file", "path", "missing.txt");

        injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"));
        injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"));
        injector.afterToolCall(call, ToolResult.success("ok"));

        assertThat(injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"))).isNull();
        assertThat(injector.afterToolCall(call, ToolResult.failure("File not found: missing.txt"))).isNull();
    }

    /**
     * 路径参数的细微差异会归一为同一指纹。
     */
    @Test
    void normalizesPathArgumentsForRepeatedFailureDetection() {
        assertThat(injector.afterToolCall(call("read_file", "path", "./dir\\missing.txt "),
                ToolResult.failure("File not found"))).isNull();
        assertThat(injector.afterToolCall(call("read_file", "path", "dir/missing.txt"),
                ToolResult.failure("File not found"))).isNull();

        String reminder = injector.afterToolCall(call("read_file", "path", ".\\dir\\missing.txt"),
                ToolResult.failure("File not found"));

        assertThat(reminder).contains("[SYSTEM REMINDER]");
    }

    /**
     * bash 的非零退出和超时输出也算无效。
     */
    @Test
    void treatsBashExitCodeAndTimeoutOutputAsIneffective() {
        ToolCall exitCall = call("bash", "command", "missing-command");

        injector.afterToolCall(exitCall, ToolResult.success("exitCode=1\nmissing-command"));
        injector.afterToolCall(exitCall, ToolResult.success("exitCode=1\nmissing-command"));
        String exitReminder = injector.afterToolCall(exitCall, ToolResult.success("exitCode=1\nmissing-command"));

        assertThat(exitReminder).contains("[SYSTEM REMINDER]");

        SystemReminderInjector timeoutInjector = new SystemReminderInjector();
        ToolCall timeoutCall = call("bash", "command", "Start-Sleep 60");
        timeoutInjector.afterToolCall(timeoutCall, ToolResult.success("[Command timed out after 30000 ms and was terminated]"));
        timeoutInjector.afterToolCall(timeoutCall, ToolResult.success("[Command timed out after 30000 ms and was terminated]"));
        String timeoutReminder = timeoutInjector.afterToolCall(timeoutCall,
                ToolResult.success("[Command timed out after 30000 ms and was terminated]"));

        assertThat(timeoutReminder).contains("[SYSTEM REMINDER]");
    }

    /**
     * 不同工具或不同关键参数不会互相累计。
     */
    @Test
    void doesNotMixDifferentToolNamesOrArguments() {
        assertThat(injector.afterToolCall(call("read_file", "path", "a.txt"),
                ToolResult.failure("File not found"))).isNull();
        assertThat(injector.afterToolCall(call("write_file", "path", "a.txt"),
                ToolResult.failure("File not found"))).isNull();
        assertThat(injector.afterToolCall(call("read_file", "path", "b.txt"),
                ToolResult.failure("File not found"))).isNull();

        assertThat(injector.afterToolCall(call("read_file", "path", "a.txt"),
                ToolResult.failure("File not found"))).isNull();
    }

    /**
     * Map 参数顺序不影响指纹。
     */
    @Test
    void sortsMapKeysBeforeFingerprinting() {
        ToolCall first = call("edit_file", "path", "App.java", "old_text", "missing", "new_text", "replacement");
        ToolCall second = call("edit_file", "new_text", "replacement", "old_text", "missing", "path", "App.java");
        ToolCall third = call("edit_file", "old_text", "missing", "path", "App.java", "new_text", "replacement");

        assertThat(injector.afterToolCall(first, ToolResult.failure("old_text not found"))).isNull();
        assertThat(injector.afterToolCall(second, ToolResult.failure("old_text not found"))).isNull();

        String reminder = injector.afterToolCall(third, ToolResult.failure("old_text not found"));

        assertThat(reminder).contains("[SYSTEM REMINDER]");
    }

    private static ToolCall call(String toolName, Object... arguments) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < arguments.length; i += 2) {
            values.put(String.valueOf(arguments[i]), arguments[i + 1]);
        }
        return new ToolCall(toolName, values);
    }
}
