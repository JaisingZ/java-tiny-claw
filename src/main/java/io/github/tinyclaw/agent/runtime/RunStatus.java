package io.github.tinyclaw.agent.runtime;

/**
 * Main Loop 运行结果状态。
 */
public enum RunStatus {
    /**
     * 模型给出完成决策并返回最终回答。
     */
    SUCCESS,

    /**
     * provider、工具或步数限制导致运行失败。
     */
    FAILED
}
