package io.github.tinyclaw.agent.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 工具层的工作区路径解析辅助。
 */
final class WorkspacePathResolver {

    private static final int MAX_SEARCH_DEPTH = 4;

    private final Path workDir;

    WorkspacePathResolver(Path workDir) {
        this.workDir = workDir.toAbsolutePath().normalize();
    }

    Path resolveRaw(String rawPath) {
        return workDir.resolve(rawPath).normalize();
    }

    boolean isInsideWorkspace(Path target) {
        return target.startsWith(workDir);
    }

    Optional<Path> resolveUniqueFilename(String rawPath) {
        Path filenamePath = Path.of(rawPath).getFileName();
        if (filenamePath == null) {
            return Optional.empty();
        }
        String filename = filenamePath.toString();
        try (Stream<Path> paths = Files.find(workDir, MAX_SEARCH_DEPTH,
                (path, attributes) -> attributes.isRegularFile()
                        && path.getFileName().toString().equals(filename))) {
            List<Path> matches = paths.limit(2).toList();
            if (matches.size() == 1) {
                return Optional.of(matches.get(0));
            }
            return Optional.empty();
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    String relativeUnix(Path target) {
        return workDir.relativize(target).toString().replace('\\', '/');
    }
}
