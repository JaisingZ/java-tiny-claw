package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据工具调用结果给模型追加 token 使用提示。
 */
final class TokenEfficiencyAdvisor {

    private static final int REPEATED_READ_THRESHOLD = 2;

    private final Map<String, Integer> successfulReadCounts = new HashMap<String, Integer>();

    String afterToolCall(AgentContext context, ToolCall call, ToolResult result) {
        if (!result.success()) {
            return null;
        }
        if (isReadFile(call)) {
            return afterSuccessfulRead(call);
        }
        if (requiresValidation(context) && validationFailed(result.output())) {
            return "[SYSTEM REMINDER] The validation failed. Fix the failing code or run a targeted check next; "
                    + "avoid long analysis and do not finish until validation passes.";
        }
        if (isWriteTool(call) && requiresValidation(context)) {
            return "[SYSTEM REMINDER] A file was just modified and this task asks for validation. "
                    + "Next, prioritize running the validation/test command such as validation.ps1; "
                    + "do not repeat-read unchanged files first.";
        }
        return null;
    }

    private String afterSuccessfulRead(ToolCall call) {
        String path = normalizedPath(call);
        if (path == null) {
            return null;
        }
        int count = successfulReadCounts.getOrDefault(path, 0) + 1;
        successfulReadCounts.put(path, count);
        if (count <= REPEATED_READ_THRESHOLD) {
            return null;
        }
        return "[SYSTEM REMINDER] You have successfully read the same read_file path " + count
                + " times: " + path + ". Stop repeating this read; modify, validate, test, or finish.";
    }

    static boolean isReadFile(ToolCall call) {
        return "read_file".equals(call.toolName());
    }

    private static boolean isWriteTool(ToolCall call) {
        return "write_file".equals(call.toolName()) || "edit_file".equals(call.toolName());
    }

    static String normalizedPath(ToolCall call) {
        Object rawPath = call.arguments().get("path");
        if (!(rawPath instanceof String)) {
            return null;
        }
        String path = ((String) rawPath).trim().replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path.isEmpty() ? null : path;
    }

    static boolean requiresValidation(AgentContext context) {
        StringBuilder content = new StringBuilder();
        append(content, context.goal());
        append(content, context.draftThought());
        append(content, context.reviewFeedback());
        append(content, context.approvedPlan());
        for (String observation : context.observations()) {
            append(content, observation);
        }
        String value = content.toString();
        String lower = value.toLowerCase(Locale.ROOT);
        return value.contains("验证")
                || lower.contains("validate")
                || lower.contains("validation.ps1")
                || lower.contains("test");
    }

    static boolean validationPassed(AgentContext context) {
        String latest = latestObservation(context);
        if (latest == null) {
            return false;
        }
        String lower = latest.toLowerCase(Locale.ROOT);
        return lower.contains("result=ok")
                || lower.contains("build success")
                || lower.contains("failures: 0")
                || lower.contains("failures=0");
    }

    private static boolean validationFailed(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("result=failed")
                || lower.contains("build failure")
                || lower.contains("failures: 1")
                || lower.contains("failures=1")) {
            return true;
        }
        int exitCodeIndex = lower.indexOf("exitcode=");
        if (exitCodeIndex < 0) {
            return false;
        }
        int valueStart = exitCodeIndex + "exitcode=".length();
        return valueStart < lower.length() && lower.charAt(valueStart) != '0';
    }

    private static String latestObservation(AgentContext context) {
        List<String> observations = context.observations();
        if (observations.isEmpty()) {
            return null;
        }
        return observations.get(observations.size() - 1);
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(value);
    }
}
