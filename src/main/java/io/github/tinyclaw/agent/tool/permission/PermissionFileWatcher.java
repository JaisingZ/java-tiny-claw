package io.github.tinyclaw.agent.tool.permission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 权限配置文件热更新 watcher。
 */
public final class PermissionFileWatcher implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionFileWatcher.class);
    private static final long DEBOUNCE_MILLIS = 200L;

    private final PermissionPolicyProvider provider;
    private final Duration interval;
    private final Thread thread;
    private volatile boolean running;
    private WatchService watchService;

    public PermissionFileWatcher(PermissionPolicyProvider provider, Duration interval) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.thread = new Thread(this::runLoop, "permission-file-watcher");
        this.thread.setDaemon(true);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread.start();
    }

    @Override
    public void close() {
        running = false;
        closeWatchService();
        thread.interrupt();
    }

    private void runLoop() {
        Path sourcePath = provider.sourcePath();
        Path parent = sourcePath == null || sourcePath.getParent() == null ? Path.of(".") : sourcePath.getParent();
        String fileName = sourcePath == null || sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString();
        try {
            watchService = parent.getFileSystem().newWatchService();
            parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Permission WatchService unavailable, polling only: {}", ex.getMessage());
        }

        FileFingerprint lastFingerprint = fileFingerprint(sourcePath);
        reload();
        while (running) {
            boolean changed = pollWatchService(fileName);
            FileFingerprint currentFingerprint = fileFingerprint(sourcePath);
            if (!currentFingerprint.equals(lastFingerprint)) {
                changed = true;
                lastFingerprint = currentFingerprint;
            }
            if (changed) {
                debounce();
                reload();
                lastFingerprint = fileFingerprint(sourcePath);
            }
            sleep(interval);
        }
    }

    private boolean pollWatchService(String fileName) {
        if (watchService == null) {
            return false;
        }
        boolean changed = false;
        WatchKey key;
        while ((key = watchService.poll()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Object context = event.context();
                if (context != null && fileName.equals(context.toString())) {
                    changed = true;
                }
            }
            key.reset();
        }
        return changed;
    }

    private void reload() {
        boolean reloaded = provider.reload();
        PermissionPolicySnapshot current = provider.current();
        if (reloaded) {
            LOGGER.info("Permission policy reloaded: path={}, rules={}, loadedAt={}",
                    current.sourcePath(), current.ruleCount(), current.loadedAt());
        } else {
            LOGGER.warn("Permission policy reload failed, keeping last known good snapshot: path={}",
                    current.sourcePath());
        }
    }

    private FileFingerprint fileFingerprint(Path sourcePath) {
        if (sourcePath == null) {
            return FileFingerprint.missing();
        }
        try {
            if (!Files.exists(sourcePath)) {
                return FileFingerprint.missing();
            }
            return new FileFingerprint(Files.getLastModifiedTime(sourcePath).toMillis(), Files.size(sourcePath));
        } catch (IOException | RuntimeException ex) {
            return FileFingerprint.missing();
        }
    }

    private record FileFingerprint(long lastModifiedMillis, long size) {

        private static FileFingerprint missing() {
            return new FileFingerprint(-1L, -1L);
        }
    }

    private void debounce() {
        sleep(Duration.ofMillis(DEBOUNCE_MILLIS));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeWatchService() {
        if (watchService == null) {
            return;
        }
        try {
            watchService.close();
        } catch (IOException ex) {
            LOGGER.warn("Failed to close permission WatchService: {}", ex.getMessage());
        }
    }
}
