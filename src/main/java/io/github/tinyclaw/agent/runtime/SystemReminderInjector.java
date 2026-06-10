package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 检测重复无效工具调用并生成运行时提醒。
 */
public final class SystemReminderInjector {

    private static final int REMINDER_THRESHOLD = 3;

    private final Map<String, Integer> ineffectiveCounts = new HashMap<String, Integer>();

    /**
     * 记录一次工具执行，必要时返回 System Reminder。
     */
    public String afterToolCall(ToolCall call, ToolResult result) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(result, "result");

        if (!isIneffective(result)) {
            clear();
            return null;
        }

        String fingerprint = fingerprint(call);
        int count = ineffectiveCounts.getOrDefault(fingerprint, 0) + 1;
        ineffectiveCounts.put(fingerprint, count);

        if (count < REMINDER_THRESHOLD) {
            return null;
        }
        return reminder(call.toolName(), count);
    }

    private boolean isIneffective(ToolResult result) {
        if (!result.success()) {
            return true;
        }
        String output = result.output();
        if (output == null) {
            return false;
        }
        String trimmed = output.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return trimmed.startsWith("exitCode=")
                || lower.contains("command timed out after");
    }

    private String fingerprint(ToolCall call) {
        return call.toolName() + "|" + normalizeArguments(call.arguments());
    }

    private String normalizeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        List<String> keys = new ArrayList<String>(arguments.keySet());
        Collections.sort(keys);
        StringBuilder builder = new StringBuilder();
        for (String key : keys) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(key).append('=').append(normalizeValue(key, arguments.get(key)));
        }
        return builder.toString();
    }

    private String normalizeValue(String key, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) value;
            return "{" + normalizeArguments(nested) + "}";
        }
        String normalized = String.valueOf(value).trim().replaceAll("\\s+", " ");
        if ("path".equals(key)) {
            normalized = normalized.replace('\\', '/');
            while (normalized.startsWith("./")) {
                normalized = normalized.substring(2);
            }
        }
        return normalized;
    }

    private void clear() {
        ineffectiveCounts.clear();
    }

    private String reminder(String toolName, int failCount) {
        return "[SYSTEM REMINDER] You have called " + toolName + " with the same effective arguments "
                + failCount + " consecutive times and the result is still ineffective. Stop repeating this "
                + "tool call, change strategy, inspect the situation from another angle, or finish and "
                + "explain what manual input is needed.";
    }
}
