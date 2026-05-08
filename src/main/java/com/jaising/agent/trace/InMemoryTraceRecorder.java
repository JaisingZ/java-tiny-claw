package com.jaising.agent.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class InMemoryTraceRecorder implements TraceRecorder {

  private final List<TraceEvent> events = new ArrayList<TraceEvent>();

  @Override
  public void record(TraceEvent event) {
    events.add(event);
  }

  @Override
  public List<TraceEvent> events() {
    return Collections.unmodifiableList(events);
  }
}
