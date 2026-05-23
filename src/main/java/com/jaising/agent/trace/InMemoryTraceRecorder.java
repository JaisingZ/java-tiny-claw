package com.jaising.agent.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存轨迹记录器
 * 主要用于测试断言
 */
public final class InMemoryTraceRecorder implements TraceRecorder {

    private final List<TraceEvent> events = new ArrayList<TraceEvent>();

    /**
     * 记录事件
     */
    @Override
    public void record(TraceEvent event) {
        events.add(event);
    }

    /**
     * 返回只读事件列表
     */
    @Override
    public List<TraceEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
