package io.github.tinyclaw.agent.communication.telegram;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
    private final String tunnel;
    private final int registrationDelaySeconds;
    private final int registrationMaxAttempts;
    private final int registrationRetryIntervalSeconds;

    public TelegramWebhookConfig(
            String token,
            String publicWebhookUrl,
            String listenHost,
            int listenPort,
            String webhookPath,
            String secretToken,
            boolean dropPendingUpdates,
            int maxConnections) {
        this(token, publicWebhookUrl, listenHost, listenPort, webhookPath, secretToken, dropPendingUpdates,
                maxConnections, "", 0, 1, 0);
    }

    public TelegramWebhookConfig(
            String token,
            String publicWebhookUrl,
            String listenHost,
            int listenPort,
            String webhookPath,
            String secretToken,
            boolean dropPendingUpdates,
            int maxConnections,
            String tunnel) {
        this(token, publicWebhookUrl, listenHost, listenPort, webhookPath, secretToken, dropPendingUpdates,
                maxConnections, tunnel, 0, 1, 0);
    }

    public TelegramWebhookConfig(
            String token,
            String publicWebhookUrl,
            String listenHost,
            int listenPort,
            String webhookPath,
            String secretToken,
            boolean dropPendingUpdates,
            int maxConnections,
            String tunnel,
            int registrationDelaySeconds,
            int registrationMaxAttempts,
            int registrationRetryIntervalSeconds) {
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
        this.tunnel = tunnel == null ? "" : tunnel;
        this.registrationDelaySeconds = parseNonNegativeInt(registrationDelaySeconds,
                "telegram.webhook.registrationDelaySeconds");
        this.registrationMaxAttempts = parsePositiveInt(registrationMaxAttempts, "telegram.webhook.registrationMaxAttempts");
        this.registrationRetryIntervalSeconds = parseNonNegativeInt(registrationRetryIntervalSeconds,
                "telegram.webhook.registrationRetryIntervalSeconds");
    }

    public static TelegramWebhookConfig fromEnv() {
        return from(System.getenv());
    }

    public static TelegramWebhookConfig load(Path path) {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load agent config: " + path, ex);
        }
        Map<String, String> values = new HashMap<String, String>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return from(values);
    }

    public static TelegramWebhookConfig loadDefault() {
        String configPath = System.getProperty("agent.config");
        if (configPath != null && !configPath.trim().isEmpty()) {
            return load(Path.of(configPath));
        }
        Path localConfig = Path.of("agent.properties");
        if (Files.exists(localConfig)) {
            return load(localConfig);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = TelegramWebhookConfig.class.getClassLoader()
                .getResourceAsStream("agent.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("agent.properties not found");
            }
            properties.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load classpath agent.properties", ex);
        }
        Map<String, String> values = new HashMap<String, String>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }
        return from(values);
    }

    public static TelegramWebhookConfig from(Map<String, String> env) {
        return new TelegramWebhookConfig(
                requiredEnv(env, "TELEGRAM_BOT_TOKEN", "telegram.bot.token"),
                optional(env, "TELEGRAM_WEBHOOK_URL", "telegram.webhook.url", ""),
                optional(env, "TELEGRAM_WEBHOOK_HOST", "telegram.webhook.host", "0.0.0.0"),
                parsePositiveInt(
                        optional(env, "TELEGRAM_WEBHOOK_PORT", "telegram.webhook.port", ""),
                        8080,
                        "TELEGRAM_WEBHOOK_PORT"),
                optional(env, "TELEGRAM_WEBHOOK_PATH", "telegram.webhook.path", "/telegram/webhook"),
                optional(env, "TELEGRAM_WEBHOOK_SECRET", "telegram.webhook.secret", ""),
                parseBoolean(optional(env, "TELEGRAM_WEBHOOK_DROP_PENDING_UPDATES",
                        "telegram.webhook.dropPendingUpdates", "false"), false),
                parsePositiveInt(optional(env, "TELEGRAM_WEBHOOK_MAX_CONNECTIONS",
                        "telegram.webhook.maxConnections", ""), 40, "TELEGRAM_WEBHOOK_MAX_CONNECTIONS"),
                optional(env, "TELEGRAM_WEBHOOK_TUNNEL", "telegram.webhook.tunnel", ""),
                parseNonNegativeInt(optional(env, "TELEGRAM_WEBHOOK_REGISTRATION_DELAY_SECONDS",
                        "telegram.webhook.registrationDelaySeconds", "0"),
                        0, "TELEGRAM_WEBHOOK_REGISTRATION_DELAY_SECONDS"),
                parsePositiveInt(optional(env, "TELEGRAM_WEBHOOK_REGISTRATION_MAX_ATTEMPTS",
                        "telegram.webhook.registrationMaxAttempts", "1"),
                        1, "TELEGRAM_WEBHOOK_REGISTRATION_MAX_ATTEMPTS"),
                parseNonNegativeInt(optional(env, "TELEGRAM_WEBHOOK_REGISTRATION_RETRY_INTERVAL_SECONDS",
                        "telegram.webhook.registrationRetryIntervalSeconds", "0"),
                        0, "TELEGRAM_WEBHOOK_REGISTRATION_RETRY_INTERVAL_SECONDS"));
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

    public String tunnel() {
        return tunnel;
    }

    public int registrationDelaySeconds() {
        return registrationDelaySeconds;
    }

    public int registrationMaxAttempts() {
        return registrationMaxAttempts;
    }

    public int registrationRetryIntervalSeconds() {
        return registrationRetryIntervalSeconds;
    }

    public TelegramWebhookConfig withPublicWebhookUrl(String publicWebhookUrl) {
        return new TelegramWebhookConfig(token, publicWebhookUrl, listenHost, listenPort, webhookPath, secretToken,
                dropPendingUpdates, maxConnections, tunnel, registrationDelaySeconds, registrationMaxAttempts,
                registrationRetryIntervalSeconds);
    }

    private static String requiredEnv(Map<String, String> env, String primaryKey, String fallbackKey) {
        String value = env.get(primaryKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = env.get(fallbackKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        throw new IllegalStateException(primaryKey + " or " + fallbackKey + " is required");
    }

    private static String optional(Map<String, String> env, String primaryKey, String fallbackKey, String defaultValue) {
        String value = env.get(primaryKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = env.get(fallbackKey);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
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

    private static int parsePositiveInt(int value, String key) {
        if (value <= 0) {
            throw new IllegalStateException(key + " must be positive: " + value);
        }
        return value;
    }

    private static int parseNonNegativeInt(int value, String key) {
        if (value < 0) {
            throw new IllegalStateException(key + " must be non-negative: " + value);
        }
        return value;
    }

    private static int parseNonNegativeInt(String value, int defaultValue, String key) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalStateException(key + " must be non-negative: " + parsed);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid value for " + key + ": " + value, ex);
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
