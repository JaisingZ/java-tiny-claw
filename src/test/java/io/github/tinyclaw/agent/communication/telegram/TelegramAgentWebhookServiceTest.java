package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tinyclaw.agent.provider.LmStudioConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TelegramAgentWebhookServiceTest {

    @Test
    void startsLocalServerThenTryCloudflareThenRegistersDynamicWebhook() {
        List<String> events = new ArrayList<String>();
        AtomicInteger tunnelPort = new AtomicInteger();
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(events);
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "secret-1", false, 40,
                "trycloudflare", 0, 1, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> {
                    tunnelPort.set(port);
                    events.add("tunnel:" + port);
                    assertThat(port).isPositive();
                    return new FakeTunnel("https://abc-def.trycloudflare.com", events);
                },
                registrarFactory);

        service.start();
        service.stop();

        assertThat(events).containsExactly(
                "tunnel:" + tunnelPort.get(),
                "register:https://abc-def.trycloudflare.com/telegram/webhook",
                "tunnel-close");
        assertThat(registrarFactory.lastConfig().secretToken()).isEqualTo("secret-1");
    }

    @Test
    void usesConfiguredPublicWebhookUrlWithoutStartingTunnel() {
        List<String> events = new ArrayList<String>();
        RecordingRegistrarFactory registrarFactory = new RecordingRegistrarFactory(events);
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "https://example.com/telegram/webhook", "127.0.0.1", 0,
                "/telegram/webhook", "", false, 40, "trycloudflare");
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> {
                    throw new AssertionError("trycloudflare tunnel must not start when public URL is configured");
                },
                registrarFactory);

        service.start();
        service.stop();

        assertThat(events).containsExactly("register:https://example.com/telegram/webhook");
    }

    @Test
    void closesTunnelWhenDynamicRegistrationFails() {
        List<String> events = new ArrayList<String>();
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40,
                "trycloudflare", 0, 1, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> new FakeTunnel("https://abc-def.trycloudflare.com", events),
                config -> new TelegramWebhookRegistrar(config, (url, body) -> {
                    throw new IllegalStateException("setWebhook failed");
                }));

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setWebhook failed");
        assertThat(events).containsExactly("tunnel-close");
    }

    @Test
    void retriesDynamicWebhookRegistrationWhenTelegramCannotResolveTryCloudflareHost() {
        List<String> events = new ArrayList<String>();
        AtomicInteger attempts = new AtomicInteger();
        TelegramWebhookConfig telegramConfig = new TelegramWebhookConfig(
                "token-1", "", "127.0.0.1", 0, "/telegram/webhook", "", false, 40,
                "trycloudflare", 0, 3, 0);
        TelegramAgentWebhookService service = new TelegramAgentWebhookService(
                telegramConfig,
                new LmStudioConfig("http://localhost:1234/v1", "model-1"),
                Path.of("."),
                2,
                false,
                port -> new FakeTunnel("https://abc-def.trycloudflare.com", events),
                config -> new TelegramWebhookRegistrar(config, (url, body) -> {
                    int attempt = attempts.incrementAndGet();
                    events.add("register-attempt-" + attempt + ":" + config.publicWebhookUrl());
                    if (attempt == 1) {
                        return new TelegramWebhookHttpResponse(400,
                                "{\"ok\":false,\"description\":\"Bad Request: bad webhook: Failed to resolve host: Name or service not known\"}");
                    }
                    return new TelegramWebhookHttpResponse(200, "{\"ok\":true}");
                }));

        service.start();
        service.stop();

        assertThat(events).containsExactly(
                "register-attempt-1:https://abc-def.trycloudflare.com/telegram/webhook",
                "register-attempt-2:https://abc-def.trycloudflare.com/telegram/webhook",
                "tunnel-close");
    }

    private static final class FakeTunnel implements TelegramAgentWebhookService.PublicTunnel {
        private final String publicBaseUrl;
        private final List<String> events;

        private FakeTunnel(String publicBaseUrl, List<String> events) {
            this.publicBaseUrl = publicBaseUrl;
            this.events = events;
        }

        @Override
        public String publicBaseUrl() {
            return publicBaseUrl;
        }

        @Override
        public void close() {
            events.add("tunnel-close");
        }
    }

    private static final class RecordingRegistrarFactory implements TelegramAgentWebhookService.RegistrarFactory {
        private final List<String> events;
        private TelegramWebhookConfig lastConfig;

        private RecordingRegistrarFactory(List<String> events) {
            this.events = events;
        }

        @Override
        public TelegramWebhookRegistrar create(TelegramWebhookConfig config) {
            lastConfig = config;
            return new TelegramWebhookRegistrar(config, (url, body) -> {
                events.add("register:" + config.publicWebhookUrl());
                return new TelegramWebhookHttpResponse(200, "{\"ok\":true}");
            });
        }

        private TelegramWebhookConfig lastConfig() {
            return lastConfig;
        }
    }
}
