package io.github.tinyclaw.agent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTraceSinkTest {

    @TempDir
    Path workDir;

    @Test
    void writesPrettyJsonTraceUnderTinyclawTraces() throws Exception {
        FileTraceSink sink = new FileTraceSink(workDir);
        TraceRecorder recorder = TraceRecorder.forSink(sink);

        try (TraceScope root = recorder.startRoot("agent.run")) {
            root.span().putAttribute("goal_preview", "hello");
        }

        Path traceDir = workDir.resolve(".tinyclaw").resolve("traces");
        List<Path> files;
        try (java.util.stream.Stream<Path> stream = Files.list(traceDir)) {
            files = stream.toList();
        }
        assertThat(files).hasSize(1);
        assertThat(files.get(0).getFileName().toString())
                .startsWith("trace-")
                .endsWith(".json");

        String json = Files.readString(files.get(0));
        assertThat(json).contains("\n  \"trace_id\"");
        JsonNode node = new ObjectMapper().readTree(json);
        assertThat(node.get("name").asText()).isEqualTo("agent.run");
        assertThat(node.get("attributes").get("goal_preview").asText()).isEqualTo("hello");
        assertThat(node.get("start_time").asText()).isNotBlank();
        assertThat(node.get("duration_ms").asLong()).isGreaterThanOrEqualTo(0L);
        assertThat(node.get("children").isArray()).isTrue();
        assertThat(node.has("trace_id")).isTrue();
        assertThat(node.has("span_id")).isTrue();
        assertThat(node.has("parent_span_id")).isTrue();
        assertThat(node.has("duration_ms")).isTrue();
    }
}
