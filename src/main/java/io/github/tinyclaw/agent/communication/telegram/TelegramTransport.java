package io.github.tinyclaw.agent.communication.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.tinyclaw.agent.communication.ChatMessage;
import io.github.tinyclaw.agent.communication.ChatMessageHandler;
import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.communication.ChatTransport;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 基于 Webhook 的 Telegram 入口。
 */
public final class TelegramTransport implements ChatTransport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramWebhookConfig config;
    private final Function<String, ChatSession> sessionFactory;
    private final TelegramWebhookRegistrar registrar;
    private HttpServer server;
    private ExecutorService executor;

    public TelegramTransport(TelegramWebhookConfig config) {
        this(config, chatId -> new TelegramSession(config.token(), chatId), new TelegramWebhookRegistrar(config));
    }

    TelegramTransport(TelegramWebhookConfig config, Function<String, ChatSession> sessionFactory,
            TelegramWebhookRegistrar registrar) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.registrar = Objects.requireNonNull(registrar, "registrar");
    }

    @Override
    public void start(ChatMessageHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if (server != null) {
            return;
        }
        try {
            registrar.register();
            server = HttpServer.create(new InetSocketAddress(config.listenHost(), config.listenPort()), 0);
            server.createContext(config.webhookPath(), exchange -> handleWebhook(exchange, handler));
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Telegram webhook server failed to start: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        server = null;
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    int listenPort() {
        if (server == null) {
            return config.listenPort();
        }
        return server.getAddress().getPort();
    }

    private void handleWebhook(HttpExchange exchange, ChatMessageHandler handler) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "method_not_allowed");
            return;
        }
        if (!secretMatches(exchange)) {
            sendResponse(exchange, 401, "unauthorized");
            return;
        }

        JsonNode update;
        try {
            update = MAPPER.readTree(exchange.getRequestBody());
        } catch (JsonProcessingException ex) {
            sendResponse(exchange, 400, "invalid_json");
            return;
        }

        ChatMessage message = toChatMessage(update);
        if (message == null) {
            sendResponse(exchange, 200, "ignored");
            return;
        }

        ChatSession session = sessionFactory.apply(message.chatId());
        try {
            handler.handle(message, session);
        } catch (RuntimeException ex) {
            session.sendError("消息处理失败：" + ex.getMessage());
        }
        sendResponse(exchange, 200, "ok");
    }

    private boolean secretMatches(HttpExchange exchange) {
        if (!hasText(config.secretToken())) {
            return true;
        }
        String actual = exchange.getRequestHeaders().getFirst(SECRET_HEADER);
        return config.secretToken().equals(actual);
    }

    private ChatMessage toChatMessage(JsonNode update) {
        JsonNode message = update.path("message");
        String text = message.path("text").asText(null);
        if (!hasText(text)) {
            return null;
        }
        String messageId = message.path("message_id").asText();
        String chatId = message.path("chat").path("id").asText();
        String senderId = message.path("from").path("id").asText();
        return new ChatMessage(messageId, chatId, senderId, text);
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
