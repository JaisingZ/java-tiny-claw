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

    private final String token;
    private final String chatId;
    private final HttpClient httpClient;

    public TelegramSession(String token, String chatId) {
        this(token, chatId, HttpClient.newHttpClient());
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
                throw new IllegalStateException("Telegram sendMessage failed: " + response.statusCode());
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
}
