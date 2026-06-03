package io.github.tinyclaw.agent.communication.telegram;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class TelegramHttpClients {

    private TelegramHttpClients() {
    }

    static HttpClient create() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        proxySelector(System.getenv()).ifPresent(builder::proxy);
        return builder.build();
    }

    static Optional<ProxySelector> proxySelector(Map<String, String> env) {
        String proxy = firstText(env, "HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy");
        if (proxy == null) {
            return Optional.empty();
        }
        URI uri = URI.create(proxy);
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || port <= 0) {
            return Optional.empty();
        }
        return Optional.of(ProxySelector.of(new InetSocketAddress(host, port)));
    }

    private static String firstText(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (hasText(value)) {
                return value.trim();
            }
            value = env.get(key.toLowerCase(Locale.ROOT));
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
