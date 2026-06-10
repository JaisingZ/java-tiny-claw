package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Telegram Webhook 注册器测试。
 */
class TelegramWebhookRegistrarTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * setWebhook 请求包含 Telegram 所需参数。
     */
    @Test
    void sendsSetWebhookRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<String>();
        TelegramWebhookConfig config = new TelegramWebhookConfig(
                "token-1", "https://example.com/telegram/webhook", "0.0.0.0", 8080,
                "/telegram/webhook", "secret-1", true, 12);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(config,
                (url, requestBody) -> {
                    body.set(requestBody);
                    return new TelegramWebhookHttpResponse(200, "{\"ok\":true}");
                });

        registrar.register();

        JsonNode json = MAPPER.readTree(body.get());
        assertThat(json.path("url").asText()).isEqualTo("https://example.com/telegram/webhook");
        assertThat(json.path("allowed_updates").get(0).asText()).isEqualTo("message");
        assertThat(json.path("secret_token").asText()).isEqualTo("secret-1");
        assertThat(json.path("drop_pending_updates").asBoolean()).isTrue();
        assertThat(json.path("max_connections").asInt()).isEqualTo(12);
    }

    /**
     * 未配置公网 URL 时不调用 Telegram API。
     */
    @Test
    void skipsRegistrationWhenPublicWebhookUrlIsBlank() {
        TelegramWebhookConfig config = new TelegramWebhookConfig(
                "token-1", " ", "0.0.0.0", 8080, "/telegram/webhook", "", false, 40);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(config,
                (url, requestBody) -> {
                    throw new AssertionError("setWebhook must not be called");
                });

        registrar.register();
    }

    /**
     * HTTP 非 2xx 时抛出清晰异常。
     */
    @Test
    void failsWhenTelegramReturnsHttpError() {
        TelegramWebhookConfig config = new TelegramWebhookConfig(
                "token-1", "https://example.com/telegram/webhook", "0.0.0.0", 8080,
                "/telegram/webhook", "", false, 40);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(config,
                (url, requestBody) -> new TelegramWebhookHttpResponse(500, "{\"ok\":false}"));

        assertThatThrownBy(registrar::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setWebhook")
                .hasMessageContaining("500");
    }

    /**
     * Telegram 返回 ok=false 时抛出 description。
     */
    @Test
    void failsWhenTelegramReturnsOkFalse() {
        TelegramWebhookConfig config = new TelegramWebhookConfig(
                "token-1", "https://example.com/telegram/webhook", "0.0.0.0", 8080,
                "/telegram/webhook", "", false, 40);
        TelegramWebhookRegistrar registrar = new TelegramWebhookRegistrar(config,
                (url, requestBody) -> new TelegramWebhookHttpResponse(200,
                        "{\"ok\":false,\"description\":\"bad url\"}"));

        assertThatThrownBy(registrar::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setWebhook")
                .hasMessageContaining("bad url");
    }
}
