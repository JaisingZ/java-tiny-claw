package com.jaising.agent.domain;

import java.util.Objects;

/**
 * main loop 的任务定义。
 * 承载任务ID和目标文本。
 */
public final class Task {

    private final String taskId;
    private final String goal;

    /**
     * 创建任务定义。
     */
    public Task(String taskId, String goal) {
        this.taskId = taskId;
        this.goal = goal;
    }

    /**
     * 获取任务ID。
     */
    public String taskId() {
        return taskId;
    }

    /**
     * 获取任务目标文本。
     */
    public String goal() {
        return goal;
    }

    /**
     * 获取任务ID（兼容 get 风格调用）。
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 获取任务目标文本（兼容 get 风格调用）。
     */
    public String getGoal() {
        return goal;
    }

    /**
     * 判断任务定义是否等价。
     */
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

    /**
     * 计算任务定义哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(taskId, goal);
    }

    /**
     * 返回便于日志的可读表示。
     */
    @Override
    public String toString() {
        return "Task{"
                + "taskId='" + taskId + '\''
                + ", goal='" + goal + '\''
                + '}';
    }
}
