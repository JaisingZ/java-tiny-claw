package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class TelegramSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void splitsLongMessagesForTelegramLimit() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        TelegramSession session = new TelegramSession("token-1", "chat-1", httpClient);

        session.sendText("a".repeat(4_500));

        assertThat(httpClient.requests).hasSize(2);
        for (HttpRequest request : httpClient.requests) {
            JsonNode body = MAPPER.readTree(bodyOf(request));
            assertThat(body.path("chat_id").asText()).isEqualTo("chat-1");
            assertThat(body.path("text").asText()).hasSizeLessThanOrEqualTo(4_096);
        }
    }

    @Test
    void failsWhenTelegramResponseBodyIsNotOk() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, "{\"ok\":false,\"description\":\"bad text\"}");
        TelegramSession session = new TelegramSession("token-1", "chat-1", httpClient);

        assertThatThrownBy(() -> session.sendText("hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Telegram sendMessage failed")
                .hasMessageContaining("bad text");
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try {
                    output.write(bytes);
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        if (!done.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("request body publisher did not complete");
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final List<HttpRequest> requests = new ArrayList<HttpRequest>();
        private final int statusCode;
        private final String responseBody;

        private RecordingHttpClient() {
            this(200, "{\"ok\":true}");
        }

        private RecordingHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            return (HttpResponse<T>) new SimpleResponse(statusCode, responseBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class SimpleResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        private SimpleResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Collections.emptyMap(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://api.telegram.org");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
