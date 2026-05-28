package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 内部思考决策
 * 只承载计划文本 不代表可执行动作
 */
public final class ThinkingDecision implements Decision {

    private final String thought;

    /**
     * 创建内部思考
     */
    public ThinkingDecision(String thought) {
        this.thought = thought;
    }

    /**
     * 执行 thought 操作。
     */
    public String thought() {
        return thought;
    }

    /**
     * 读取 Thought。
     */
    public String getThought() {
        return thought;
    }

    /**
     * 比较对象是否相等。
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
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(thought);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "ThinkingDecision{"
                + "thought='" + thought + '\''
                + '}';
    }
}
