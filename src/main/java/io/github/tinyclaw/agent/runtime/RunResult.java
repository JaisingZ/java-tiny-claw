package io.github.tinyclaw.agent.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Main Loop 的最终运行结果。
 * 直接暴露状态、最终回答、失败原因、步数和观测。
 */
public final class RunResult {

    private final RunStatus status;
    private final String finalAnswer;
    private final String failureReason;
    private final int stepCount;
    private final List<String> observations;
    private final RunMetrics metrics;

    /**
     * 创建运行结果。
     */
    public RunResult(RunStatus status, String finalAnswer, String failureReason, int stepCount,
            List<String> observations) {
        this(status, finalAnswer, failureReason, stepCount, observations, RunMetrics.empty());
    }

    /**
     * 创建带运行指标的运行结果。
     */
    public RunResult(RunStatus status, String finalAnswer, String failureReason, int stepCount,
            List<String> observations, RunMetrics metrics) {
        this.status = status;
        this.finalAnswer = finalAnswer;
        this.failureReason = failureReason;
        this.stepCount = stepCount;
        this.metrics = metrics == null ? RunMetrics.empty() : metrics;
        List<String> safeObservations = observations == null
                ? Collections.<String>emptyList()
                : observations;
        this.observations = Collections.unmodifiableList(new ArrayList<String>(safeObservations));
    }

    /**
     * 创建成功结果。
     */
    public static RunResult success(int stepCount, List<String> observations, String answer) {
        return new RunResult(RunStatus.SUCCESS, answer, null, stepCount, observations);
    }

    public static RunResult success(int stepCount, List<String> observations, String answer, RunMetrics metrics) {
        return new RunResult(RunStatus.SUCCESS, answer, null, stepCount, observations, metrics);
    }

    /**
     * 创建失败结果。
     */
    public static RunResult failed(int stepCount, List<String> observations, String reason) {
        return new RunResult(RunStatus.FAILED, null, reason, stepCount, observations);
    }

    public static RunResult failed(int stepCount, List<String> observations, String reason, RunMetrics metrics) {
        return new RunResult(RunStatus.FAILED, null, reason, stepCount, observations, metrics);
    }

    /**
     * 返回运行状态。
     */
    public RunStatus status() {
        return status;
    }

    /**
     * 返回模型给出的最终回答，失败时为 null。
     */
    public String finalAnswer() {
        return finalAnswer;
    }

    /**
     * 返回失败原因，成功时为 null。
     */
    public String failureReason() {
        return failureReason;
    }

    /**
     * 返回已完成的工具步数。
     */
    public int stepCount() {
        return stepCount;
    }

    /**
     * 返回工具观测快照。
     */
    public List<String> observations() {
        return observations;
    }

    /**
     * 返回本次运行的可观测指标。
     */
    public RunMetrics metrics() {
        return metrics;
    }

    /**
     * JavaBean 风格状态 getter。
     */
    public RunStatus getStatus() {
        return status;
    }

    /**
     * JavaBean 风格最终回答 getter。
     */
    public String getFinalAnswer() {
        return finalAnswer;
    }

    /**
     * JavaBean 风格失败原因 getter。
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * JavaBean 风格步数 getter。
     */
    public int getStepCount() {
        return stepCount;
    }

    /**
     * JavaBean 风格观测 getter。
     */
    public List<String> getObservations() {
        return observations;
    }

    public RunMetrics getMetrics() {
        return metrics();
    }

    /**
     * 按值比较，便于测试断言。
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
                && Objects.equals(observations, that.observations)
                && Objects.equals(metrics, that.metrics);
    }

    /**
     * 计算对象哈希值。
     */
    @Override
    public int hashCode() {
        return Objects.hash(status, finalAnswer, failureReason, stepCount, observations, metrics);
    }
}
