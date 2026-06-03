package io.github.tinyclaw.agent.domain;

import java.util.Objects;

/**
 * main loop 的结束决策。
 * 用于告知循环已经得到最终答案并可返回给调用方。
 */
public final class FinishDecision implements Decision {

    private final String answer;

    /**
     * 创建结束决策。
     */
    public FinishDecision(String answer) {
        this.answer = answer;
    }

    /**
     * 读取最终答案文本。
     */
    public String answer() {
        return answer;
    }

    /**
     * 读取最终答案文本（兼容 get 风格调用）。
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 判断答案是否等价。
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
     * 计算决策哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(answer);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "FinishDecision{"
                + "answer='" + answer + '\''
                + '}';
    }
}
