package io.github.tinyclaw.agent.communication.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tinyclaw.agent.communication.ChatSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Telegram 聊天会话。
 */
public final class TelegramSession implements ChatSession {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_MESSAGE_LENGTH = 4096;

    private final String token;
    private final String chatId;
    private final HttpClient httpClient;

    public TelegramSession(String token, String chatId) {
        this(token, chatId, TelegramHttpClients.create());
    }

    TelegramSession(String token, String chatId, HttpClient httpClient) {
        this.token = token;
        this.chatId = chatId;
        this.httpClient = httpClient;
    }

    @Override
    public void sendText(String text) {
        sendMessage(text);
    }

    @Override
    public void sendStatus(String text) {
        sendMessage(text);
    }

    @Override
    public void sendError(String text) {
        sendMessage(text);
    }

    private void sendMessage(String text) {
        String message = text == null ? "" : text;
        for (int start = 0; start < message.length(); start += MAX_MESSAGE_LENGTH) {
            sendMessageChunk(message.substring(start, Math.min(start + MAX_MESSAGE_LENGTH, message.length())));
        }
        if (message.isEmpty()) {
            sendMessageChunk("");
        }
    }

    private void sendMessageChunk(String text) {
        try {
            Map<String, String> payload = new LinkedHashMap<String, String>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            String body = MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl("sendMessage")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Telegram sendMessage failed: " + response.statusCode()
                        + " " + response.body());
            }
            if (!telegramOk(response.body())) {
                throw new IllegalStateException("Telegram sendMessage failed: " + telegramDescription(response.body()));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Telegram sendMessage failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram sendMessage interrupted", ex);
        }
    }

    String apiUrl(String method) {
        return "https://api.telegram.org/bot" + token + "/" + method;
    }

    private boolean telegramOk(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return false;
        }
        return MAPPER.readTree(body).path("ok").asBoolean(false);
    }

    private String telegramDescription(String body) {
        try {
            return MAPPER.readTree(body).path("description").asText("(no description)");
        } catch (IOException ex) {
            return "invalid response body";
        }
    }
}
