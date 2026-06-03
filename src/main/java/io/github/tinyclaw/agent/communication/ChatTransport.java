package io.github.tinyclaw.agent.communication;

/**
 * 通信平台入口。
 */
public interface ChatTransport extends AutoCloseable {

    /**
     * 启动消息接收。
     */
    void start(ChatMessageHandler handler);

    /**
     * 停止消息接收。
     */
    void stop();

    @Override
    default void close() {
        stop();
    }
}
