package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.ToolCall;

/**
 * 将工具错误转换为可执行的恢复观测。
 */
public final class ErrorRecoveryAdvisor {

    /**
     * 为工具失败生成模型可读的错误观测。
     */
    public String advise(ToolCall call, String errorMessage) {
        String toolName = call == null ? "unknown" : call.toolName();
        String safeError = errorMessage == null ? "" : errorMessage;
        String observation = "Error executing " + toolName + ": " + safeError;
        String hint = hint(toolName, safeError);
        if (hint.isEmpty()) {
            return observation;
        }
        return observation + "\n\n[Recovery Hint] " + hint;
    }

    private String hint(String toolName, String errorMessage) {
        String lowerError = errorMessage.toLowerCase();
        if (lowerError.startsWith("unknown tool:")) {
            return "Check the available tools and call one of their exact tool names.";
        }
        if ("edit_file".equals(toolName)) {
            return editFileHint(lowerError);
        }
        if ("read_file".equals(toolName) || "write_file".equals(toolName)) {
            return fileHint(lowerError);
        }
        if ("bash".equals(toolName)) {
            return bashHint(lowerError);
        }
        return "";
    }

    private String editFileHint(String lowerError) {
        if (lowerError.contains("old_text not found")) {
            return "Use read_file first, then edit again with the exact current text, including indentation "
                    + "and line endings.";
        }
        if (lowerError.contains("old_text matched") || lowerError.contains("provide more context")) {
            return "Add surrounding lines to old_text so the replacement target is unique.";
        }
        if (lowerError.contains("file not found")) {
            return fileMissingHint();
        }
        if (lowerError.contains("path escapes workspace")) {
            return "Use a relative path inside the workspace. Do not use absolute paths or parent traversal.";
        }
        return "";
    }

    private String fileHint(String lowerError) {
        if (lowerError.contains("file not found")) {
            return fileMissingHint();
        }
        if (lowerError.contains("path escapes workspace")) {
            return "Use a relative path inside the workspace. Do not use absolute paths or parent traversal.";
        }
        if (lowerError.contains("missing required argument")) {
            return "Check the tool schema and provide all required arguments exactly.";
        }
        return "";
    }

    private String bashHint(String lowerError) {
        if (lowerError.contains("missing required argument")) {
            return "Provide the required command argument.";
        }
        if (lowerError.contains("failed to start command")) {
            return "Check whether the command exists in this environment or use an available alternative.";
        }
        if (lowerError.contains("command interrupted")) {
            return "Use a shorter command or split the work into smaller commands.";
        }
        return "";
    }

    private String fileMissingHint() {
        return "Use bash to inspect the workspace first, for example Get-ChildItem on Windows or find/ls on Unix, "
                + "then retry with the correct relative path.";
    }
}
