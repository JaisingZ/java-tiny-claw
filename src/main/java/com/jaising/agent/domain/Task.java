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

    /**
     * 执行 taskId 操作。
     */
    public String taskId() {
        return taskId;
    }

    /**
     * 执行 goal 操作。
     */
    public String goal() {
        return goal;
    }

    /**
     * 读取 TaskId。
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 读取 Goal。
     */
    public String getGoal() {
        return goal;
    }

    /**
     * 比较对象是否相等。
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
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(taskId, goal);
    }

    /**
     * 返回可读字符串。
     */
    @Override
    public String toString() {
        return "Task{"
                + "taskId='" + taskId + '\''
                + ", goal='" + goal + '\''
                + '}';
    }
}
