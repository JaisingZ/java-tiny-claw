package io.github.tinyclaw.agent.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 将 benchmark 报告写为 JSON。
 */
public final class BenchmarkReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public Path write(Path reportsDir, BenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        Path dir = reportsDir == null
                ? Path.of(".tinyclaw", "bench", "reports")
                : reportsDir;
        try {
            Files.createDirectories(dir);
            Path reportFile = dir.resolve("bench-" + LocalDateTime.now().format(FORMATTER) + ".json");
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
            return reportFile;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write benchmark report: " + dir, ex);
        }
    }
}
