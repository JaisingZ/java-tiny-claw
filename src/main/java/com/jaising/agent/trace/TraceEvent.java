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

    /**
     * 执行 type 操作。
     */
    public TraceEventType type() {
        return type;
    }

    /**
     * 执行 taskId 操作。
     */
    public String taskId() {
        return taskId;
    }

    /**
     * 执行 step 操作。
     */
    public int step() {
        return step;
    }

    /**
     * 执行 detail 操作。
     */
    public String detail() {
        return detail;
    }

    /**
     * 执行 durationMillis 操作。
     */
    public long durationMillis() {
        return durationMillis;
    }

    /**
     * 读取 Type。
     */
    public TraceEventType getType() {
        return type;
    }

    /**
     * 读取 TaskId。
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 读取 Step。
     */
    public int getStep() {
        return step;
    }

    /**
     * 读取 Detail。
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 读取 DurationMillis。
     */
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

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, taskId, step, detail, durationMillis);
    }
}
