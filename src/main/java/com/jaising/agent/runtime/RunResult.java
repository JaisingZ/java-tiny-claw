package com.jaising.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 运行结果
 * 直接暴露最终状态和观测
 */
public final class RunResult {

    private final RunStatus status;
    private final String finalAnswer;
    private final String failureReason;
    private final int stepCount;
    private final List<String> observations;

    /**
     * 创建运行结果
     */
    public RunResult(RunStatus status, String finalAnswer, String failureReason, int stepCount,
            List<String> observations) {
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.failureReason = failureReason;
        this.stepCount = stepCount;
        List<String> safeObservations = observations == null
                ? Collections.<String>emptyList()
                : observations;
        this.observations = Collections.unmodifiableList(new ArrayList<String>(safeObservations));
    }

    public static RunResult success(int stepCount, List<String> observations, String answer) {
        return new RunResult(RunStatus.SUCCESS, answer, null, stepCount, observations);
    }

    public static RunResult failed(int stepCount, List<String> observations, String reason) {
        return new RunResult(RunStatus.FAILED, null, reason, stepCount, observations);
    }

    public RunStatus status() {
        return status;
    }

    public String finalAnswer() {
        return finalAnswer;
    }

    public String failureReason() {
        return failureReason;
    }

    public int stepCount() {
        return stepCount;
    }

    public List<String> observations() {
        return observations;
    }

    public RunStatus getStatus() {
        return status;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getStepCount() {
        return stepCount;
    }

    public List<String> getObservations() {
        return observations;
    }

    /**
     * 按值比较
     * 便于测试断言
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RunResult)) {
            return false;
        }
        RunResult that = (RunResult) other;
        return stepCount == that.stepCount
                && status == that.status
                && Objects.equals(finalAnswer, that.finalAnswer)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(observations, that.observations);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(status, finalAnswer, failureReason, stepCount, observations);
    }
}
