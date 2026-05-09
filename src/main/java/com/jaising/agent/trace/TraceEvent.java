package com.jaising.agent.trace;

import java.util.Objects;

/**
 * 轨迹事件
 * 保存事件类型 任务 步数和详情
 */
public final class TraceEvent {

  private final TraceEventType type;
  private final String taskId;
  private final int step;
  private final String detail;
  private final long durationMillis;

  /**
   * 创建轨迹事件
   */
  public TraceEvent(TraceEventType type, String taskId, int step, String detail,
      long durationMillis) {
    this.type = type;
    this.taskId = taskId;
    this.step = step;
    this.detail = detail;
    this.durationMillis = durationMillis;
  }

  public TraceEventType type() {
    return type;
  }

  public String taskId() {
    return taskId;
  }

  public int step() {
    return step;
  }

  public String detail() {
    return detail;
  }

  public long durationMillis() {
    return durationMillis;
  }

  public TraceEventType getType() {
    return type;
  }

  public String getTaskId() {
    return taskId;
  }

  public int getStep() {
    return step;
  }

  public String getDetail() {
    return detail;
  }

  public long getDurationMillis() {
    return durationMillis;
  }

  /**
   * 按字段比较
   * 便于测试检查
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof TraceEvent)) {
      return false;
    }
    TraceEvent that = (TraceEvent) other;
    return step == that.step
        && durationMillis == that.durationMillis
        && type == that.type
        && Objects.equals(taskId, that.taskId)
        && Objects.equals(detail, that.detail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, taskId, step, detail, durationMillis);
  }
}
