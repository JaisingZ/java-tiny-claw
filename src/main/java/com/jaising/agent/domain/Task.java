package com.jaising.agent.domain;

import java.util.Objects;

/**
 * 任务定义
 * 只有任务标识和目标
 */
public final class Task {

  private final String taskId;
  private final String goal;

  /**
   * 创建任务
   * 保持数据最小
   */
  public Task(String taskId, String goal) {
    this.taskId = taskId;
    this.goal = goal;
  }

  public String taskId() {
    return taskId;
  }

  public String goal() {
    return goal;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getGoal() {
    return goal;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Task)) {
      return false;
    }
    Task that = (Task) other;
    return Objects.equals(taskId, that.taskId) && Objects.equals(goal, that.goal);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taskId, goal);
  }

  @Override
  public String toString() {
    return "Task{"
        + "taskId='" + taskId + '\''
        + ", goal='" + goal + '\''
        + '}';
  }
}
