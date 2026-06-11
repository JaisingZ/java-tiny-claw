package io.github.tinyclaw.agent.observability;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Span 生命周期句柄。
 */
public final class TraceScope implements AutoCloseable {

    private final TraceSpan span;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    TraceScope(TraceSpan span, Runnable onClose) {
        if (span == null) {
            throw new NullPointerException("span");
        }
        this.span = span;
        this.onClose = onClose;
    }

    public TraceSpan span() {
        return span;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        span.end();
        if (onClose != null) {
            onClose.run();
        }
    }
}
