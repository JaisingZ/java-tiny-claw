package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 根据工具调用结果给模型追加 token 使用提示。
 */
final class TokenEfficiencyAdvisor {

    private static final int REPEATED_READ_THRESHOLD = 2;
    private static final int FAILURE_PREVIEW_MAX_CHARS = 360;

    private final Map<String, Integer> successfulReadCounts = new HashMap<String, Integer>();
    private boolean codeChangeGuidanceEmitted;
    private boolean validationPending;
    private boolean validationPassed;

    String afterToolCall(AgentContext context, ToolCall call, ToolResult result) {
        if (isBashTool(call)) {
            return afterBashToolCall(context, call, result);
        }
        if (!result.success()) {
            return null;
        }
        if (isReadFile(call)) {
            return afterSuccessfulRead(context, call);
        }
        if (isWriteTool(call) && requiresValidation(context)) {
            validationPending = true;
            validationPassed = false;
            return "[SYSTEM REMINDER] A file was just modified and this task asks for validation. "
                    + "Next, prioritize running the relevant validation/test command; "
                    + "do not repeat-read unchanged files first.";
        }
        return null;
    }

    boolean validationPassed() {
        return validationPassed;
    }

    boolean validationPending() {
        return validationPending;
    }

    String compactValidationFailureObservation(AgentContext context, ToolCall call, ToolResult result) {
        if (!isValidationFailure(context, call, result)) {
            return null;
        }
        String output = result.success() ? result.output() : result.errorMessage();
        StringBuilder compacted = new StringBuilder();
        compacted.append("Validation failed.\n");
        compacted.append(summarizeFailure(output)).append('\n');
        compacted.append(validationFailureReminder(output));
        return compacted.toString();
    }

    private String afterBashToolCall(AgentContext context, ToolCall call, ToolResult result) {
        if (!requiresValidation(context)) {
            return null;
        }

        validationPending = false;
        if (!result.success()) {
            validationPassed = false;
            return null;
        }

        String output = result.output();
        if (validationSucceeded(output) || successfulValidationCommand(call, output)) {
            validationPassed = true;
            return null;
        }

        validationPassed = false;
        if (validationFailed(output)) {
            return validationFailureReminder(output);
        }
        return null;
    }

    private String afterSuccessfulRead(AgentContext context, ToolCall call) {
        String path = normalizedPath(call);
        if (path == null) {
            return null;
        }
        if (requiresValidation(context) && isSourcePath(path) && !codeChangeGuidanceEmitted) {
            codeChangeGuidanceEmitted = true;
            return "[SYSTEM REMINDER] You have read source code for a validation task. "
                    + "Make the smallest correct change next. If replacing a large block or full file, "
                    + "use write_file with the complete new content; do not use edit_file with full-file old_text.";
        }
        int count = successfulReadCounts.getOrDefault(path, 0) + 1;
        successfulReadCounts.put(path, count);
        if (count <= REPEATED_READ_THRESHOLD) {
            return null;
        }
        return "[SYSTEM REMINDER] You have successfully read the same read_file path " + count
                + " times: " + path + ". Stop repeating this read; modify, validate, test, or finish.";
    }

    private boolean isSourcePath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".scala")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".py")
                || lower.endsWith(".go")
                || lower.endsWith(".rs")
                || lower.endsWith(".c")
                || lower.endsWith(".cpp")
                || lower.endsWith(".h");
    }

    static boolean isReadFile(ToolCall call) {
        return "read_file".equals(call.toolName());
    }

    private static boolean isWriteTool(ToolCall call) {
        return "write_file".equals(call.toolName()) || "edit_file".equals(call.toolName());
    }

    private static boolean isBashTool(ToolCall call) {
        return "bash".equals(call.toolName());
    }

    private static boolean isValidationFailure(AgentContext context, ToolCall call, ToolResult result) {
        if (!isBashTool(call) || !requiresValidation(context)) {
            return false;
        }
        if (!result.success()) {
            return true;
        }
        return validationFailed(result.output());
    }

    private static boolean successfulValidationCommand(ToolCall call, String output) {
        if (!isValidationCommand(call)) {
            return false;
        }
        String lower = lower(output);
        return lower.contains("command executed successfully with no output")
                || lower.contains("validation ok");
    }

    private static boolean isValidationCommand(ToolCall call) {
        Object rawCommand = call.arguments().get("command");
        if (!(rawCommand instanceof String)) {
            return false;
        }
        String lower = ((String) rawCommand).toLowerCase(Locale.ROOT);
        return lower.contains("mvn")
                || lower.contains("test")
                || lower.contains("javac")
                || lower.contains("java ");
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
                || lower.contains("test");
    }

    private static boolean validationSucceeded(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String lower = lower(output);
        return lower.contains("result=ok")
                || lower.contains("build success")
                || lower.contains("failures: 0")
                || lower.contains("failures=0");
    }

    private static boolean validationFailed(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String lower = lower(output);
        if (lower.contains("result=failed")
                || lower.contains("build failure")
                || lower.contains("failures: 1")
                || lower.contains("failures=1")
                || lower.contains("command timed out after")
                || lower.contains("unmappable character")
                || lower.contains("不可映射字符")) {
            return true;
        }
        int exitCodeIndex = lower.indexOf("exitcode=");
        if (exitCodeIndex < 0) {
            return false;
        }
        int valueStart = exitCodeIndex + "exitcode=".length();
        return valueStart < lower.length() && lower.charAt(valueStart) != '0';
    }

    private static String validationFailureReminder(String output) {
        String lower = lower(output);
        if (lower.contains("unmappable character") || lower.contains("不可映射字符")) {
            return "[SYSTEM REMINDER] Validation failed with javac encoding failure. "
                    + "Priority: keep changes minimal and fix only current file; "
                    + "remove non-ASCII comments/strings or save Java source as UTF-8, then rerun validation.";
        }
        return "[SYSTEM REMINDER] The validation failed. Fix the failing code or run a targeted check next; "
                + "avoid long analysis and do not finish until validation passes. Fix action: inspect the "
                + "latest error, make the smallest code change, then rerun validation.";
    }

    private static String summarizeFailure(String output) {
        if (output == null || output.isBlank()) {
            return "Output: <empty>";
        }
        String normalized = output.replace("\r\n", "\n").replace('\r', '\n').trim();
        String lower = lower(normalized);
        if (lower.contains("unmappable character") || lower.contains("不可映射字符")) {
            return "Cause: javac encoding failure; remove non-ASCII comments/strings or write UTF-8 Java source.";
        }
        if (lower.contains("command timed out after")) {
            return "Cause: validation command timed out.\nOutput preview: " + preview(normalized);
        }
        return "Output preview: " + preview(normalized);
    }

    private static String preview(String value) {
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= FAILURE_PREVIEW_MAX_CHARS) {
            return compact;
        }
        return compact.substring(0, FAILURE_PREVIEW_MAX_CHARS) + "...(truncated)";
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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
