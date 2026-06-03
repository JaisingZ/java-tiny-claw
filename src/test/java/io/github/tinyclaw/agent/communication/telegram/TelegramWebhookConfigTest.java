package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Telegram Webhook 配置测试。
 */
class TelegramWebhookConfigTest {

    /**
     * 环境变量缺省时使用保守默认值。
     */
    @Test
    void loadsDefaultsFromEnvironment() {
        TelegramWebhookConfig config = TelegramWebhookConfig.from(newEnv("TELEGRAM_BOT_TOKEN", "token-1"));

        assertThat(config.token()).isEqualTo("token-1");
        assertThat(config.publicWebhookUrl()).isEqualTo("");
        assertThat(config.listenHost()).isEqualTo("0.0.0.0");
        assertThat(config.listenPort()).isEqualTo(8080);
        assertThat(config.webhookPath()).isEqualTo("/telegram/webhook");
        assertThat(config.secretToken()).isEqualTo("");
        assertThat(config.dropPendingUpdates()).isFalse();
        assertThat(config.maxConnections()).isEqualTo(40);
    }

    /**
     * token 必填。
     */
    @Test
    void requiresBotToken() {
        assertThatThrownBy(() -> TelegramWebhookConfig.from(Collections.<String, String>emptyMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_BOT_TOKEN");
    }

    /**
     * 显式环境变量覆盖默认监听配置。
     */
    @Test
    void overridesDefaultsFromEnvironment() {
        Map<String, String> env = newEnv("TELEGRAM_BOT_TOKEN", "token-1");
        env.put("TELEGRAM_WEBHOOK_URL", "https://example.com/hook");
        env.put("TELEGRAM_WEBHOOK_SECRET", "secret-1");
        env.put("TELEGRAM_WEBHOOK_HOST", "127.0.0.1");
        env.put("TELEGRAM_WEBHOOK_PORT", "9090");
        env.put("TELEGRAM_WEBHOOK_PATH", "/hook");

        TelegramWebhookConfig config = TelegramWebhookConfig.from(env);

        assertThat(config.publicWebhookUrl()).isEqualTo("https://example.com/hook");
        assertThat(config.secretToken()).isEqualTo("secret-1");
        assertThat(config.listenHost()).isEqualTo("127.0.0.1");
        assertThat(config.listenPort()).isEqualTo(9090);
        assertThat(config.webhookPath()).isEqualTo("/hook");
    }

    private static Map<String, String> newEnv(String key, String value) {
        Map<String, String> env = new HashMap<String, String>();
        env.put(key, value);
        return env;
    }
}
