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
        Collections.<String>emptyList(), null, null, null);
  }

  /**
   * 推进一步
   * 只增加步数 不改终态
   */
  public AgentState advance() {
    return new AgentState(taskId, goal, status, stepCount + 1, observations, finalAnswer,
        failureReason, pauseReason);
  }

  /**
   * 追加一次观测
   * 记录工具输出
   */
  public AgentState observe(String observation) {
    List<String> nextObservations = new ArrayList<String>(observations);
    nextObservations.add(observation);
    return new AgentState(taskId, goal, status, stepCount, nextObservations, finalAnswer,
        failureReason, pauseReason);
  }

  /**
   * 标记成功
   * 清空失败和暂停原因
   */
  public AgentState finish(String answer) {
    return new AgentState(taskId, goal, AgentStatus.SUCCESS, stepCount, observations, answer,
        null, null);
  }

  /**
   * 标记失败
   * 保留已有观测
   */
  public AgentState fail(String reason) {
    return new AgentState(taskId, goal, AgentStatus.FAILED, stepCount, observations,
        finalAnswer, reason, pauseReason);
  }

  /*
   * 标记暂停
   * 预留给人工介入
   */
  public AgentState pause(String reason) {
    return new AgentState(taskId, goal, AgentStatus.PAUSED, stepCount, observations,
        finalAnswer, failureReason, reason);
  }

  public String taskId() {
    return taskId;
  }

  public String goal() {
    return goal;
  }

  public AgentStatus status() {
    return status;
  }

  public int stepCount() {
    return stepCount;
  }

  public List<String> observations() {
    return observations;
  }

  public String finalAnswer() {
    return finalAnswer;
  }

  public String failureReason() {
    return failureReason;
  }

  public String pauseReason() {
    return pauseReason;
  }

  public String getTaskId() {
    return taskId;
  }

  public String getGoal() {
    return goal;
  }

  public AgentStatus getStatus() {
    return status;
  }

  public int getStepCount() {
    return stepCount;
  }

  public List<String> getObservations() {
    return observations;
  }

  public String getFinalAnswer() {
    return finalAnswer;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getPauseReason() {
    return pauseReason;
  }

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
        && Objects.equals(finalAnswer, that.finalAnswer)
        && Objects.equals(failureReason, that.failureReason)
        && Objects.equals(pauseReason, that.pauseReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taskId, goal, status, stepCount, observations, finalAnswer,
        failureReason, pauseReason);
  }
}
