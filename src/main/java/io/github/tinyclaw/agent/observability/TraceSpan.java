package io.github.tinyclaw.agent.observability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次结构化链路追踪中的时间跨度。
 */
public final class TraceSpan {

    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final String startTime;
    private String endTime;
    private long durationMillis;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<TraceSpan> children = new ArrayList<>();
    private final transient long startNanos;
    private final transient AtomicBoolean ended = new AtomicBoolean(false);

    TraceSpan(String traceId, String parentSpanId, String name) {
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.parentSpanId = parentSpanId;
        this.name = Objects.requireNonNull(name, "name");
        this.spanId = UUID.randomUUID().toString();
        this.startTime = Instant.now().toString();
        this.startNanos = System.nanoTime();
    }

    public static TraceSpan root(String name) {
        return new TraceSpan(UUID.randomUUID().toString(), null, name);
    }

    TraceSpan child(String childName) {
        TraceSpan child = new TraceSpan(traceId, spanId, childName);
        synchronized (children) {
            children.add(child);
        }
        return child;
    }

    void end() {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        synchronized (this) {
            if (endTime != null) {
                return;
            }
            endTime = Instant.now().toString();
            durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        }
    }

    public void putAttribute(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        synchronized (attributes) {
            attributes.put(key, value);
        }
    }

    @JsonProperty("trace_id")
    public String traceId() {
        return traceId;
    }

    @JsonProperty("span_id")
    public String spanId() {
        return spanId;
    }

    @JsonProperty("parent_span_id")
    public String parentSpanId() {
        return parentSpanId;
    }

    @JsonProperty("name")
    public String name() {
        return name;
    }

    @JsonProperty("start_time")
    public String startTime() {
        return startTime;
    }

    @JsonProperty("end_time")
    public synchronized String endTime() {
        return endTime;
    }

    @JsonProperty("duration_ms")
    public synchronized long durationMillis() {
        return durationMillis;
    }

    @JsonProperty("attributes")
    public Map<String, Object> attributes() {
        synchronized (attributes) {
            return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(attributes));
        }
    }

    @JsonProperty("children")
    public List<TraceSpan> children() {
        synchronized (children) {
            return Collections.unmodifiableList(new ArrayList<TraceSpan>(children));
        }
    }

    @JsonIgnore
    public boolean ended() {
        return endTime() != null;
    }
}
