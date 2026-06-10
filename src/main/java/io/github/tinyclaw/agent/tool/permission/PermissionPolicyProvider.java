package io.github.tinyclaw.agent.tool.permission;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 动态权限策略提供者，保存 last-known-good 快照。
 */
public final class PermissionPolicyProvider {

    private final Path sourcePath;
    private final SnapshotLoader loader;
    private final AtomicReference<PermissionPolicySnapshot> snapshot;

    public PermissionPolicyProvider(Path sourcePath, PermissionPolicySnapshot initialSnapshot) {
        this(sourcePath, initialSnapshot, () -> PermissionPolicySnapshot.load(sourcePath));
    }

    public PermissionPolicyProvider(Path sourcePath, PermissionPolicySnapshot initialSnapshot, SnapshotLoader loader) {
        this.sourcePath = sourcePath;
        this.loader = Objects.requireNonNull(loader, "loader");
        this.snapshot = new AtomicReference<PermissionPolicySnapshot>(
                Objects.requireNonNull(initialSnapshot, "initialSnapshot"));
    }

    public static PermissionPolicyProvider load(Path sourcePath) {
        return new PermissionPolicyProvider(sourcePath, PermissionPolicySnapshot.load(sourcePath));
    }

    public PermissionPolicySnapshot current() {
        return snapshot.get();
    }

    public boolean reload() {
        try {
            snapshot.set(loader.load());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    Path sourcePath() {
        return sourcePath;
    }

    @FunctionalInterface
    public interface SnapshotLoader {

        PermissionPolicySnapshot load();
    }
}
