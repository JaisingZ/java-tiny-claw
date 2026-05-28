package com.jaising.agent.trace;

import java.util.List;

/**
 * 轨迹记录器
 * 记录主循环关键事件
 */
public interface TraceRecorder {
    /**
     * 记录轨迹事件。
     */
    void record(TraceEvent event);

    /**
     * 返回已记录事件。
     */
    List<TraceEvent> events();
}
