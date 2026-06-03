package io.github.tinyclaw.agent.domain;

/**
 * main loop 决策阶段。
 * 用于区分模型在“思考”和“执行”两段。
 */
public enum DecisionPhase {
    /**
     * 主循环进入思考阶段，产出 Thought 决策。
     */
    THINKING,

    /**
     * 主循环进入执行阶段，产出工具或结束决策。
     */
    ACTION
}
