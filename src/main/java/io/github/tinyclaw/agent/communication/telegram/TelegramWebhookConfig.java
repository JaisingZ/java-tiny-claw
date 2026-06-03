package io.github.tinyclaw.agent.communication.telegram;

import java.util.Map;

/**
 * Telegram Webhook 配置。
 */
public final class TelegramWebhookConfig {

    private final String token;
    private final String publicWebhookUrl;
    private final String listenHost;
    private final int listenPort;
    private final String webhookPath;
    private final String secretToken;
    private final boolean dropPendingUpdates;
    private final int maxConnections;

    public TelegramWebhookConfig(
            String token,
            String publicWebhookUrl,
            String listenHost,
            int listenPort,
            String webhookPath,
            String secretToken,
            boolean dropPendingUpdates,
            int maxConnections) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required");
        }
        this.token = token;
        this.publicWebhookUrl = publicWebhookUrl;
        this.listenHost = defaultIfBlank(listenHost, "0.0.0.0");
        this.listenPort = listenPort;
        this.webhookPath = defaultIfBlank(webhookPath, "/telegram/webhook");
        this.secretToken = secretToken == null ? "" : secretToken;
        this.dropPendingUpdates = dropPendingUpdates;
        this.maxConnections = maxConnections;
    }

    public static TelegramWebhookConfig fromEnv() {
        return from(System.getenv());
    }

    public static TelegramWebhookConfig from(Map<String, String> env) {
        return new TelegramWebhookConfig(
                requiredEnv(env, "TELEGRAM_BOT_TOKEN"),
                defaultIfBlank(env.get("TELEGRAM_WEBHOOK_URL"), ""),
                defaultIfBlank(env.get("TELEGRAM_WEBHOOK_HOST"), "0.0.0.0"),
                parsePositiveInt(env.get("TELEGRAM_WEBHOOK_PORT"), 8080, "TELEGRAM_WEBHOOK_PORT"),
                defaultIfBlank(env.get("TELEGRAM_WEBHOOK_PATH"), "/telegram/webhook"),
                nullToBlank(env.get("TELEGRAM_WEBHOOK_SECRET")),
                false,
                40);
    }

    public String token() {
        return token;
    }

    public String publicWebhookUrl() {
        return publicWebhookUrl;
    }

    public String listenHost() {
        return listenHost;
    }

    public int listenPort() {
        return listenPort;
    }

    public String webhookPath() {
        return webhookPath;
    }

    public String secretToken() {
        return secretToken;
    }

    public boolean dropPendingUpdates() {
        return dropPendingUpdates;
    }

    public int maxConnections() {
        return maxConnections;
    }

    private static String requiredEnv(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return value;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static int parsePositiveInt(String value, int defaultValue, String key) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalStateException(key + " must be positive: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid value for " + key + ": " + value, ex);
        }
    }
}
