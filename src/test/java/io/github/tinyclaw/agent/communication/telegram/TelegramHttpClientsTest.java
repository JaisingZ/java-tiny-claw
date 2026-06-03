package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramHttpClientsTest {

    @Test
    void usesHttpsProxyEnvironmentVariable() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("HTTPS_PROXY", "http://127.0.0.1:10888");

        List<Proxy> proxies = TelegramHttpClients.proxySelector(env)
                .orElseThrow()
                .select(URI.create("https://api.telegram.org"));

        assertThat(proxies).hasSize(1);
        InetSocketAddress address = (InetSocketAddress) proxies.get(0).address();
        assertThat(address.getHostString()).isEqualTo("127.0.0.1");
        assertThat(address.getPort()).isEqualTo(10888);
    }

    @Test
    void ignoresBlankProxyEnvironmentVariable() {
        Map<String, String> env = new HashMap<String, String>();
        env.put("HTTPS_PROXY", " ");

        assertThat(TelegramHttpClients.proxySelector(env)).isEmpty();
    }
}
