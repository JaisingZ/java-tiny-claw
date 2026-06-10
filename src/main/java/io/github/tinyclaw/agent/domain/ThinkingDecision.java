package io.github.tinyclaw.agent.domain;

import java.util.Objects;

/**
 * main loop 的思考决策。
 * 仅作为模型的中间推理文本，不触发工具调用。
 */
public final class ThinkingDecision implements Decision {

    private final String thought;

    /**
     * 创建思考决策。
     */
    public ThinkingDecision(String thought) {
        this.thought = thought;
    }

    /**
     * 获取思考内容。
     */
    public String thought() {
        return thought;
    }

    /**
     * 获取思考内容（兼容 get 风格调用）。
     */
    public String getThought() {
        return thought;
    }

    /**
     * 判断思考内容是否等价。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ThinkingDecision)) {
            return false;
        }
        ThinkingDecision that = (ThinkingDecision) other;
        return Objects.equals(thought, that.thought);
    }

    /**
     * 计算思考决策哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(thought);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "ThinkingDecision{"
                + "thought='" + thought + '\''
                + '}';
    }
}
