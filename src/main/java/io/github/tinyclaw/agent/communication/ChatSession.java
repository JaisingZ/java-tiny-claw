package io.github.tinyclaw.agent.communication;

/**
 * 通信平台输出会话。
 */
public interface ChatSession {

    /**
     * 发送普通文本。
     */
    void sendText(String text);

    /**
     * 发送运行状态。
     */
    void sendStatus(String text);

    /**
     * 发送错误消息。
     */
    void sendError(String text);
}
