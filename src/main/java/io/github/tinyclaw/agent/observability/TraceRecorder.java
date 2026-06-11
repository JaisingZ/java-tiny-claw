package io.github.tinyclaw.agent.observability;

import java.util.Objects;

/**
 * 创建并导出 Agent 运行期结构化 Trace。
 */
public final class TraceRecorder {

    private static final TraceRecorder NOOP = new TraceRecorder(span -> {
    }, true);

    private final TraceSink sink;
    private final boolean noop;

    private TraceRecorder(TraceSink sink, boolean noop) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.noop = noop;
    }

    public static TraceRecorder noop() {
        return NOOP;
    }

    public static TraceRecorder forSink(TraceSink sink) {
        return new TraceRecorder(sink, false);
    }

    public TraceScope startRoot(String name) {
        Objects.requireNonNull(name, "name");
        TraceSpan span = TraceSpan.root(name);
        return new TraceScope(span, () -> export(span));
    }

    public TraceScope startChild(TraceSpan parent, String name) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(name, "name");
        return new TraceScope(parent.child(name), null);
    }

    private void export(TraceSpan root) {
        if (!noop) {
            sink.accept(root);
        }
    }

    @FunctionalInterface
    public interface TraceSink {

        void accept(TraceSpan span);
    }
}
