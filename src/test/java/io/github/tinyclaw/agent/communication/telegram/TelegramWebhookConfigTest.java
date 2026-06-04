package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Telegram Webhook 配置测试。
 */
class TelegramWebhookConfigTest {

    @TempDir
    Path tempDir;

    /**
     * 未配置可选项时使用保守默认值。
     */
    @Test
    void loadsDefaultsFromProperties() {
        TelegramWebhookConfig config = TelegramWebhookConfig.from(newValues("telegram.bot.token", "token-1"));

        assertThat(config.token()).isEqualTo("token-1");
        assertThat(config.publicWebhookUrl()).isEqualTo("");
        assertThat(config.listenHost()).isEqualTo("0.0.0.0");
        assertThat(config.listenPort()).isEqualTo(8080);
        assertThat(config.webhookPath()).isEqualTo("/telegram/webhook");
        assertThat(config.secretToken()).isEqualTo("");
        assertThat(config.dropPendingUpdates()).isFalse();
        assertThat(config.maxConnections()).isEqualTo(40);
        assertThat(config.registrationDelaySeconds()).isEqualTo(0);
        assertThat(config.registrationMaxAttempts()).isEqualTo(1);
        assertThat(config.registrationRetryIntervalSeconds()).isEqualTo(0);
    }

    /**
     * 从 agent.properties key 读取 Bot Token。
     */
    @Test
    void loadsBotTokenFromPropertyName() {
        TelegramWebhookConfig config = TelegramWebhookConfig.from(newValues("telegram.bot.token", "token-1"));

        assertThat(config.token()).isEqualTo("token-1");
    }

    /**
     * 从 agent.properties 文件读取 Telegram 配置。
     */
    @Test
    void loadsFromPropertiesFile() throws Exception {
        Path configPath = tempDir.resolve("agent.properties");
        Files.writeString(configPath, "telegram.bot.token=token-1\n"
                + "telegram.webhook.url=https://example.com/hook\n"
                + "telegram.webhook.secret=secret-1\n"
                + "telegram.webhook.host=127.0.0.1\n"
                + "telegram.webhook.port=9090\n"
                + "telegram.webhook.path=/hook\n"
                + "telegram.webhook.tunnel=trycloudflare\n"
                + "telegram.webhook.dropPendingUpdates=true\n"
                + "telegram.webhook.maxConnections=12\n"
                + "telegram.webhook.registrationDelaySeconds=60\n"
                + "telegram.webhook.registrationMaxAttempts=3\n"
                + "telegram.webhook.registrationRetryIntervalSeconds=20\n");

        TelegramWebhookConfig config = TelegramWebhookConfig.load(configPath);

        assertThat(config.token()).isEqualTo("token-1");
        assertThat(config.publicWebhookUrl()).isEqualTo("https://example.com/hook");
        assertThat(config.secretToken()).isEqualTo("secret-1");
        assertThat(config.listenHost()).isEqualTo("127.0.0.1");
        assertThat(config.listenPort()).isEqualTo(9090);
        assertThat(config.webhookPath()).isEqualTo("/hook");
        assertThat(config.tunnel()).isEqualTo("trycloudflare");
        assertThat(config.dropPendingUpdates()).isTrue();
        assertThat(config.maxConnections()).isEqualTo(12);
        assertThat(config.registrationDelaySeconds()).isEqualTo(60);
        assertThat(config.registrationMaxAttempts()).isEqualTo(3);
        assertThat(config.registrationRetryIntervalSeconds()).isEqualTo(20);
    }

    /**
     * token 必填。
     */
    @Test
    void requiresBotToken() {
        assertThatThrownBy(() -> TelegramWebhookConfig.from(Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("telegram.bot.token");
    }

    /**
     * 显式 properties 覆盖默认监听配置。
     */
    @Test
    void overridesDefaultsFromProperties() {
        Map<String, String> values = newValues("telegram.bot.token", "token-1");
        values.put("telegram.webhook.url", "https://example.com/hook");
        values.put("telegram.webhook.secret", "secret-1");
        values.put("telegram.webhook.host", "127.0.0.1");
        values.put("telegram.webhook.port", "9090");
        values.put("telegram.webhook.path", "/hook");

        TelegramWebhookConfig config = TelegramWebhookConfig.from(values);

        assertThat(config.publicWebhookUrl()).isEqualTo("https://example.com/hook");
        assertThat(config.secretToken()).isEqualTo("secret-1");
        assertThat(config.listenHost()).isEqualTo("127.0.0.1");
        assertThat(config.listenPort()).isEqualTo(9090);
        assertThat(config.webhookPath()).isEqualTo("/hook");
    }

    private static Map<String, String> newValues(String key, String value) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(key, value);
        return values;
    }
}
