package com.jaising.agent.trace;

import java.util.List;

/**
 * 轨迹记录器
 * 记录主循环关键事件
 */
public interface TraceRecorder {
  void record(TraceEvent event);

  List<TraceEvent> events();
}
