package com.jaising.agent.trace;

import java.util.List;

public interface TraceRecorder {
  void record(TraceEvent event);

  List<TraceEvent> events();
}
