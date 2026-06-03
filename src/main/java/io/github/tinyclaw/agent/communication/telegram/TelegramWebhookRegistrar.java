package io.github.tinyclaw.agent.communication.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Telegram webhook 注册器。
 */
public class TelegramWebhookRegistrar {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final TelegramWebhookConfig config;
    private final TelegramWebhookHttpClient httpClient;

    public TelegramWebhookRegistrar(TelegramWebhookConfig config) {
        this(config, defaultHttpClient());
    }

    TelegramWebhookRegistrar(TelegramWebhookConfig config, TelegramWebhookHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    public void register() {
        if (!hasText(config.publicWebhookUrl())) {
            return;
        }
        String requestBody = buildRequestBody();
        String endpoint = telegramSetWebhookUrl();
        try {
            TelegramWebhookHttpResponse response = httpClient.send(endpoint, requestBody);
            JsonNode responseBody = parseResponseBody(response);

            int statusCode = response.statusCode();
            boolean ok = responseBody.path("ok").asBoolean(false);
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException(errorMessage(statusCode, responseBody));
            }
            if (!ok) {
                throw new IllegalStateException(errorMessage(statusCode, responseBody));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Telegram setWebhook failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram setWebhook failed: " + ex.getMessage(), ex);
        }
    }

    String telegramSetWebhookUrl() {
        return TELEGRAM_API_BASE + config.token() + "/setWebhook";
    }

    private String buildRequestBody() {
        ArrayNode allowedUpdates = MAPPER.createArrayNode().add("message");
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("url", config.publicWebhookUrl());
        payload.set("allowed_updates", allowedUpdates);
        payload.put("drop_pending_updates", config.dropPendingUpdates());
        payload.put("max_connections", config.maxConnections());
        if (hasText(config.secretToken())) {
            payload.put("secret_token", config.secretToken());
        }
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Telegram setWebhook payload serialization failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode parseResponseBody(TelegramWebhookHttpResponse response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return MAPPER.createObjectNode().put("ok", false).put("description", "empty response body");
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Telegram setWebhook failed to parse response JSON: "
                    + ex.getMessage(), ex);
        }
    }

    private String errorMessage(int statusCode, JsonNode responseBody) {
        return "Telegram setWebhook failed: status=" + statusCode
                + ", description=" + responseBody.path("description").asText("(no description)");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static TelegramWebhookHttpClient defaultHttpClient() {
        HttpClient client = HttpClient.newHttpClient();
        return (url, body) -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new TelegramWebhookHttpResponse(response.statusCode(), response.body());
        };
    }
}

@FunctionalInterface
interface TelegramWebhookHttpClient {

    TelegramWebhookHttpResponse send(String url, String body) throws IOException, InterruptedException;
}

record TelegramWebhookHttpResponse(int statusCode, String body) {
}
