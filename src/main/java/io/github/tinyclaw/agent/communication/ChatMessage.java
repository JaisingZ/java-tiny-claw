package io.github.tinyclaw.agent.communication;

import java.util.Objects;

/**
 * 通信平台输入消息。
 */
public final class ChatMessage {

    private final String messageId;
    private final String chatId;
    private final String senderId;
    private final String text;

    public ChatMessage(String messageId, String chatId, String senderId, String text) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.senderId = senderId;
        this.text = text;
    }

    public String messageId() {
        return messageId;
    }

    public String chatId() {
        return chatId;
    }

    public String senderId() {
        return senderId;
    }

    public String text() {
        return text;
    }

    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatMessage)) {
            return false;
        }
        ChatMessage that = (ChatMessage) other;
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(chatId, that.chatId)
                && Objects.equals(senderId, that.senderId)
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, chatId, senderId, text);
    }
}
