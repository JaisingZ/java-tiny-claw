package io.github.tinyclaw.agent.provider;

import java.util.Objects;

/**
 * 单次模型调用返回的 token 用量。
 */
public final class ModelUsage {

    private static final ModelUsage EMPTY = new ModelUsage(0, 0, 0);

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    public ModelUsage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = Math.max(0, totalTokens);
    }

    public static ModelUsage empty() {
        return EMPTY;
    }

    public int promptTokens() {
        return promptTokens;
    }

    public int getPromptTokens() {
        return promptTokens();
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int getCompletionTokens() {
        return completionTokens();
    }

    public int totalTokens() {
        return totalTokens;
    }

    public int getTotalTokens() {
        return totalTokens();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelUsage)) {
            return false;
        }
        ModelUsage that = (ModelUsage) other;
        return promptTokens == that.promptTokens
                && completionTokens == that.completionTokens
                && totalTokens == that.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }
}
