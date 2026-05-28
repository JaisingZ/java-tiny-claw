package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 结束决策
 * 表示任务已经可以收口
 */
public final class FinishDecision implements Decision {

    private final String answer;

    /**
     * 创建结束决策
     * 携带最终答案
     */
    public FinishDecision(String answer) {
        this.answer = answer;
    }

    /**
     * 读取最终答案
     * 供运行时写回状态
     */
    public String answer() {
        return answer;
    }

    /**
     * 读取 Answer。
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 比较对象是否相等。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FinishDecision)) {
            return false;
        }
        FinishDecision that = (FinishDecision) other;
        return Objects.equals(answer, that.answer);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(answer);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "FinishDecision{"
                + "answer='" + answer + '\''
                + '}';
    }
}
