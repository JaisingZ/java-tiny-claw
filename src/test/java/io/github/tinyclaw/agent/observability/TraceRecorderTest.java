package io.github.tinyclaw.agent.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TraceRecorderTest {

    @Test
    void recordsRootAndChildSpans() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);

        try (TraceScope root = recorder.startRoot("agent.run")) {
            root.span().putAttribute("task", "demo");
            try (TraceScope child = recorder.startChild(root.span(), "llm.action")) {
                child.span().putAttribute("phase", "ACTION");
            }
        }

        assertThat(exported).hasSize(1);
        TraceSpan root = exported.get(0);
        assertThat(root.name()).isEqualTo("agent.run");
        assertThat(root.parentSpanId()).isNull();
        assertThat(root.endTime()).isNotNull();
        assertThat(root.durationMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(root.attributes()).containsEntry("task", "demo");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).isEqualTo("llm.action");
        assertThat(root.children().get(0).parentSpanId()).isEqualTo(root.spanId());
        assertThat(root.children().get(0).attributes()).containsEntry("phase", "ACTION");
    }

    @Test
    void closeIsIdempotent() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);

        TraceScope root = recorder.startRoot("agent.run");
        root.close();
        root.close();

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).endTime()).isNotNull();
    }

    @Test
    void noopRecorderDoesNotExport() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.noop();

        try (TraceScope root = recorder.startRoot("agent.run")) {
            root.span().putAttribute("task", "noop");
        }

        assertThat(exported).isEmpty();
    }

    @Test
    void keepsConcurrentChildren() throws Exception {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try (TraceScope root = recorder.startRoot("agent.run")) {
            int childCount = 40;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<Future<?>>();
            for (int i = 0; i < childCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try (TraceScope child = recorder.startChild(root.span(), "tool.execute")) {
                        child.span().putAttribute("index", index);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).children()).hasSize(40);
    }

    @Test
    void supportsNestedChildren() {
        List<TraceSpan> exported = new ArrayList<TraceSpan>();
        TraceRecorder recorder = TraceRecorder.forSink(exported::add);

        try (TraceScope root = recorder.startRoot("agent.run")) {
            try (TraceScope child = recorder.startChild(root.span(), "llm.action")) {
                try (TraceScope grandChild = recorder.startChild(child.span(), "tool.execute")) {
                    grandChild.span().putAttribute("tool_name", "echo");
                }
            }
        }

        assertThat(exported).hasSize(1);
        TraceSpan root = exported.get(0);
        TraceSpan action = root.children().get(0);
        assertThat(action.children()).hasSize(1);
        assertThat(action.children().get(0).name()).isEqualTo("tool.execute");
        assertThat(action.children().get(0).attributes()).containsEntry("tool_name", "echo");
    }
}
