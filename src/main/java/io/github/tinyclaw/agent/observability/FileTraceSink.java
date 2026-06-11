package io.github.tinyclaw.agent.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 将 Trace 写入工作区本地 JSON 文件。
 */
public final class FileTraceSink implements TraceRecorder.TraceSink {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final Path workDir;

    public FileTraceSink(Path workDir) {
        this.workDir = Objects.requireNonNull(workDir, "workDir");
    }

    @Override
    public void accept(TraceSpan span) {
        if (span == null) {
            return;
        }
        Path traceDir = workDir.resolve(".tinyclaw").resolve("traces");
        String fileName = "trace-" + span.traceId() + ".json";
        Path traceFile = traceDir.resolve(fileName);
        try {
            Files.createDirectories(traceDir);
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(traceFile.toFile(), span);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write trace file: " + traceFile, ex);
        }
    }
}
