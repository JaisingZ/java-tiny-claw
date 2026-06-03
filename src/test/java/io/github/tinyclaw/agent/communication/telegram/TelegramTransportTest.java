package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.communication.ChatMessage;
import io.github.tinyclaw.agent.communication.ChatMessageHandler;
import io.github.tinyclaw.agent.communication.ChatSession;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Telegram Webhook 入口测试。
 */
class TelegramTransportTest {

    /**
     * 合法文本 update 会转为 ChatMessage。
     */
    @Test
    void dispatchesTextMessageFromWebhookPost() throws Exception {
        RecordingSession session = new RecordingSession();
        AtomicReference<ChatMessage> received = new AtomicReference<ChatMessage>();
        TelegramTransport transport = transport("", session);
        transport.start((message, chatSession) -> received.set(message));

        HttpResponse<String> response = post(transport, textUpdate(), null);

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received.get()).isEqualTo(new ChatMessage("42", "1001", "2002", "hello"));
    }

    /**
     * 非文本 update 直接确认，不触发 handler。
     */
    @Test
    void ignoresNonTextMessageWithOkResponse() throws Exception {
        AtomicReference<ChatMessage> received = new AtomicReference<ChatMessage>();
        TelegramTransport transport = transport("", new RecordingSession());
        transport.start((message, chatSession) -> received.set(message));

        HttpResponse<String> response = post(transport,
                "{\"update_id\":1,\"message\":{\"message_id\":42,\"chat\":{\"id\":1001}}}", null);

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received.get()).isNull();
    }

    /**
     * secret token 匹配时放行。
     */
    @Test
    void acceptsRequestWhenSecretTokenMatches() throws Exception {
        AtomicReference<ChatMessage> received = new AtomicReference<ChatMessage>();
        TelegramTransport transport = transport("secret-1", new RecordingSession());
        transport.start((message, chatSession) -> received.set(message));

        HttpResponse<String> response = post(transport, textUpdate(), "secret-1");

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received.get()).isNotNull();
    }

    /**
     * secret token 不匹配时拒绝请求。
     */
    @Test
    void rejectsRequestWhenSecretTokenDoesNotMatch() throws Exception {
        AtomicReference<ChatMessage> received = new AtomicReference<ChatMessage>();
        TelegramTransport transport = transport("secret-1", new RecordingSession());
        transport.start((message, chatSession) -> received.set(message));

        HttpResponse<String> response = post(transport, textUpdate(), "wrong");

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(received.get()).isNull();
    }

    /**
     * 非法 JSON 返回 400。
     */
    @Test
    void rejectsMalformedJson() throws Exception {
        TelegramTransport transport = transport("", new RecordingSession());
        transport.start((message, chatSession) -> {
            throw new AssertionError("handler must not be called");
        });

        HttpResponse<String> response = post(transport, "{", null);

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(400);
    }

    /**
     * handler 异常会回传错误消息，但 webhook 请求仍返回 200。
     */
    @Test
    void sendsErrorMessageWhenHandlerFailsButAcknowledgesWebhook() throws Exception {
        RecordingSession session = new RecordingSession();
        TelegramTransport transport = transport("", session);
        transport.start((message, chatSession) -> {
            throw new IllegalStateException("boom");
        });

        HttpResponse<String> response = post(transport, textUpdate(), null);

        transport.stop();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(session.errors()).containsExactly("消息处理失败：boom");
    }

    private static TelegramTransport transport(String secretToken, ChatSession session) throws IOException {
        int port = freePort();
        TelegramWebhookConfig config = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", port, "/telegram/webhook", secretToken, false, 40);
        return new TelegramTransport(config, chatId -> session, new NoopRegistrar());
    }

    private static HttpResponse<String> post(TelegramTransport transport, String body, String secretToken)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + transport.listenPort() + "/telegram/webhook"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (secretToken != null) {
            builder.header("X-Telegram-Bot-Api-Secret-Token", secretToken);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String textUpdate() {
        return "{\"update_id\":1,\"message\":{\"message_id\":42,"
                + "\"chat\":{\"id\":1001},\"from\":{\"id\":2002},\"text\":\"hello\"}}";
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class RecordingSession implements ChatSession {
        private final List<String> errors = new ArrayList<String>();

        @Override
        public void sendText(String text) {
        }

        @Override
        public void sendStatus(String text) {
        }

        @Override
        public void sendError(String text) {
            errors.add(text);
        }

        private List<String> errors() {
            return errors;
        }
    }

    private static final class NoopRegistrar extends TelegramWebhookRegistrar {
        private NoopRegistrar() {
            super(new TelegramWebhookConfig("token-1", "", "127.0.0.1", 1,
                    "/telegram/webhook", "", false, 40));
        }

        @Override
        public void register() {
        }
    }
}
