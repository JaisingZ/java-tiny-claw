package io.github.tinyclaw.agent.communication.telegram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地临时 trycloudflare 隧道。
 */
public final class TryCloudflareTunnel implements TelegramAgentWebhookService.PublicTunnel {

    private static final Pattern URL_PATTERN = Pattern.compile("https://[A-Za-z0-9-]+\\.trycloudflare\\.com");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final TunnelProcess process;
    private final String publicBaseUrl;
    private final Thread outputReader;

    public static TryCloudflareTunnel start(int listenPort) {
        String localUrl = "http://127.0.0.1:" + listenPort;
        try {
            Process process = new ProcessBuilder("cloudflared", "tunnel", "--url", localUrl, "--no-autoupdate")
                    .redirectErrorStream(true)
                    .start();
            return new TryCloudflareTunnel(new ProcessBackedTunnelProcess(process), DEFAULT_TIMEOUT);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start cloudflared. Ensure cloudflared is installed in PATH.", ex);
        }
    }

    TryCloudflareTunnel(TunnelProcess process, Duration timeout) {
        this.process = Objects.requireNonNull(process, "process");
        CompletableFuture<String> urlFuture = new CompletableFuture<String>();
        this.outputReader = new Thread(() -> readOutput(process.output(), urlFuture), "trycloudflare-output");
        this.outputReader.setDaemon(true);
        this.outputReader.start();
        this.publicBaseUrl = awaitPublicUrl(urlFuture, timeout);
    }

    @Override
    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    @Override
    public void close() {
        process.destroy();
    }

    private String awaitPublicUrl(CompletableFuture<String> urlFuture, Duration timeout) {
        try {
            return urlFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            close();
            throw new IllegalStateException("Interrupted while waiting for trycloudflare URL", ex);
        } catch (ExecutionException ex) {
            close();
            throw new IllegalStateException("Failed to read trycloudflare URL: " + ex.getMessage(), ex);
        } catch (TimeoutException ex) {
            close();
            throw new IllegalStateException("Timed out waiting for trycloudflare URL. Check cloudflared output.", ex);
        }
    }

    private void readOutput(InputStream output, CompletableFuture<String> urlFuture) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(output, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = URL_PATTERN.matcher(line);
                if (matcher.find()) {
                    urlFuture.complete(matcher.group());
                }
            }
            if (!urlFuture.isDone()) {
                urlFuture.completeExceptionally(new IllegalStateException("cloudflared exited before URL was emitted"));
            }
        } catch (IOException ex) {
            if (!urlFuture.isDone()) {
                urlFuture.completeExceptionally(ex);
            }
        }
    }

    interface TunnelProcess {

        InputStream output();

        void destroy();
    }

    private static final class ProcessBackedTunnelProcess implements TunnelProcess {
        private final Process process;

        private ProcessBackedTunnelProcess(Process process) {
            this.process = process;
        }

        @Override
        public InputStream output() {
            return process.getInputStream();
        }

        @Override
        public void destroy() {
            process.destroy();
        }
    }
}
