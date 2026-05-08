package com.jaising.agent.trace;

public enum TraceEventType {
  MODEL_REQUEST,
  MODEL_RESPONSE,
  TOOL_CALL,
  TOOL_RESULT,
  FINISHED,
  FAILED,
  PAUSED
}
