package com.jaising.agent.trace;

/**
 * 轨迹事件类型
 * 覆盖主循环关键节点
 */
public enum TraceEventType {
  THINKING_REQUEST,
  THINKING_RESPONSE,
  MODEL_REQUEST,
  MODEL_RESPONSE,
  TOOL_CALL,
  TOOL_RESULT,
  FINISHED,
  FAILED,
  PAUSED
}
