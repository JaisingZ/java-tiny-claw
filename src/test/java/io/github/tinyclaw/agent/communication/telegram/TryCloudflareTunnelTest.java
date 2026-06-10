package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class TryCloudflareTunnelTest {

    @Test
    void parsesTryCloudflareUrlFromProcessOutput() {
        FakeTunnelProcess process = new FakeTunnelProcess(
                "info starting\n"
                        + "Your quick Tunnel has been created! Visit it at:\n"
                        + "https://abc-def.trycloudflare.com\n");

        TryCloudflareTunnel tunnel = new TryCloudflareTunnel(process, Duration.ofSeconds(1));

        assertThat(tunnel.publicBaseUrl()).isEqualTo("https://abc-def.trycloudflare.com");
    }

    @Test
    void closesUnderlyingProcess() {
        FakeTunnelProcess process = new FakeTunnelProcess("https://abc-def.trycloudflare.com\n");
        TryCloudflareTunnel tunnel = new TryCloudflareTunnel(process, Duration.ofSeconds(1));

        tunnel.close();

        assertThat(process.destroyed.get()).isTrue();
    }

    private static final class FakeTunnelProcess implements TryCloudflareTunnel.TunnelProcess {
        private final InputStream output;
        private final AtomicBoolean destroyed = new AtomicBoolean();

        private FakeTunnelProcess(String output) {
            this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream output() {
            return output;
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }
    }
}
