package com.jaising.agent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agent 状态
 * 保存任务进度 观测结果和终态信息
 */
public final class AgentState {

    private final String taskId;
    private final String goal;
    private final AgentStatus status;
    private final int stepCount;
    private final List<String> observations;
    private final String lastThought;
    private final String finalAnswer;
    private final String failureReason;
    private final String pauseReason;

    /**
     * 反序列化入口
     * 保证状态可以落盘和恢复
     */
    @JsonCreator
    public AgentState(
            @JsonProperty("taskId") String taskId,
            @JsonProperty("goal") String goal,
            @JsonProperty("status") AgentStatus status,
            @JsonProperty("stepCount") int stepCount,
            @JsonProperty("observations") List<String> observations,
            @JsonProperty("lastThought") String lastThought,
            @JsonProperty("finalAnswer") String finalAnswer,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("pauseReason") String pauseReason) {
        this.taskId = taskId;
        this.goal = goal;
        this.status = status;
        this.stepCount = stepCount;
        List<String> safeObservations = observations == null
                ? Collections.<String>emptyList()
                : observations;
        this.observations = Collections.unmodifiableList(new ArrayList<String>(safeObservations));
        this.lastThought = lastThought;
        this.finalAnswer = finalAnswer;
        this.failureReason = failureReason;
        this.pauseReason = pauseReason;
    }

    /**
     * 创建新任务状态
     * 初始为运行态
     */
    public static AgentState create(Task task) {
        return new AgentState(task.taskId(), task.goal(), AgentStatus.RUNNING, 0,
                Collections.<String>emptyList(), null, null, null, null);
    }

    /**
     * 推进一步
     * 只增加步数 不改终态
     */
    public AgentState advance() {
        return new AgentState(taskId, goal, status, stepCount + 1, observations, lastThought, finalAnswer,
                failureReason, pauseReason);
    }

    /**
     * 追加一次观测
     * 记录工具输出
     */
    public AgentState observe(String observation) {
        List<String> nextObservations = new ArrayList<String>(observations);
        nextObservations.add(observation);
        return new AgentState(taskId, goal, status, stepCount, nextObservations, lastThought, finalAnswer,
                failureReason, pauseReason);
    }

    /**
     * 记录最近一次内部思考
     * 不写入工具观测
     */
    public AgentState think(String thought) {
        return new AgentState(taskId, goal, status, stepCount, observations, thought, finalAnswer,
                failureReason, pauseReason);
    }

    /**
     * 标记成功
     * 清空失败和暂停原因
     */
    public AgentState finish(String answer) {
        return new AgentState(taskId, goal, AgentStatus.SUCCESS, stepCount, observations, lastThought, answer,
                null, null);
    }

    /**
     * 标记失败
     * 保留已有观测
     */
    public AgentState fail(String reason) {
        return new AgentState(taskId, goal, AgentStatus.FAILED, stepCount, observations,
                lastThought, finalAnswer, reason, pauseReason);
    }

    /**
     * 标记暂停
     * 预留给人工介入
     */
    public AgentState pause(String reason) {
        return new AgentState(taskId, goal, AgentStatus.PAUSED, stepCount, observations,
                lastThought, finalAnswer, failureReason, reason);
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
     * 执行 status 操作。
     */
    public AgentStatus status() {
        return status;
    }

    /**
     * 执行 stepCount 操作。
     */
    public int stepCount() {
        return stepCount;
    }

    /**
     * 执行 observations 操作。
     */
    public List<String> observations() {
        return observations;
    }

    /**
     * 执行 lastThought 操作。
     */
    public String lastThought() {
        return lastThought;
    }

    /**
     * 执行 finalAnswer 操作。
     */
    public String finalAnswer() {
        return finalAnswer;
    }

    /**
     * 执行 failureReason 操作。
     */
    public String failureReason() {
        return failureReason;
    }

    /**
     * 执行 pauseReason 操作。
     */
    public String pauseReason() {
        return pauseReason;
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
     * 读取 Status。
     */
    public AgentStatus getStatus() {
        return status;
    }

    /**
     * 读取 StepCount。
     */
    public int getStepCount() {
        return stepCount;
    }

    /**
     * 读取 Observations。
     */
    public List<String> getObservations() {
        return observations;
    }

    /**
     * 读取 LastThought。
     */
    public String getLastThought() {
        return lastThought;
    }

    /**
     * 读取 FinalAnswer。
     */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    /**
     * 读取 FailureReason。
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * 读取 PauseReason。
     */
    public String getPauseReason() {
        return pauseReason;
    }

    /**
     * 比较对象是否相等。
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AgentState)) {
            return false;
        }
        AgentState that = (AgentState) other;
        return stepCount == that.stepCount
                && Objects.equals(taskId, that.taskId)
                && Objects.equals(goal, that.goal)
                && status == that.status
                && Objects.equals(observations, that.observations)
                && Objects.equals(lastThought, that.lastThought)
                && Objects.equals(finalAnswer, that.finalAnswer)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(pauseReason, that.pauseReason);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(taskId, goal, status, stepCount, observations, lastThought, finalAnswer,
                failureReason, pauseReason);
    }
}
