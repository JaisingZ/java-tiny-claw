package io.github.tinyclaw.agent.domain;

/**
 * 会话消息类型。
 */
public enum SessionMessageKind {

    /**
     * 用户输入消息。
     */
    USER,

    /**
     * 助手输出消息。
     */
    ASSISTANT,

    /**
     * 工具执行观测消息。
     */
    OBSERVATION
}
