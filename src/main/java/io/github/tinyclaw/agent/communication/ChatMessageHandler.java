package io.github.tinyclaw.agent.communication;

/**
 * 通信消息处理器。
 */
public interface ChatMessageHandler {

    /**
     * 处理一条平台消息。
     */
    void handle(ChatMessage message, ChatSession session);
}
