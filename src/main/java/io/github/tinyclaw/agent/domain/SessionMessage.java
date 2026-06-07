package io.github.tinyclaw.agent.domain;

import java.util.Objects;

/**
 * 会话中的一条消息，包含角色与文本内容。
 */
public final class SessionMessage {

    private final SessionMessageKind kind;
    private final String content;

    /**
     * 创建会话消息。
     */
    public SessionMessage(SessionMessageKind kind, String content) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.content = content == null ? "" : content;
    }

    /**
     * 创建用户消息。
     */
    public static SessionMessage user(String content) {
        return new SessionMessage(SessionMessageKind.USER, content);
    }

    /**
     * 创建助手消息。
     */
    public static SessionMessage assistant(String content) {
        return new SessionMessage(SessionMessageKind.ASSISTANT, content);
    }

    /**
     * 创建观测消息。
     */
    public static SessionMessage observation(String content) {
        return new SessionMessage(SessionMessageKind.OBSERVATION, content);
    }

    /**
     * 获取消息类型。
     */
    public SessionMessageKind kind() {
        return kind;
    }

    /**
     * 获取消息类型（兼容 get 风格）。
     */
    public SessionMessageKind getKind() {
        return kind;
    }

    /**
     * 获取消息内容。
     */
    public String content() {
        return content;
    }

    /**
     * 获取消息内容（兼容 get 风格）。
     */
    public String getContent() {
        return content;
    }

    /**
     * 比较消息内容与类型是否等价。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionMessage)) {
            return false;
        }
        SessionMessage that = (SessionMessage) other;
        return kind == that.kind && Objects.equals(content, that.content);
    }

    /**
     * 计算消息哈希。
     */
    @Override
    public int hashCode() {
        return Objects.hash(kind, content);
    }
}
