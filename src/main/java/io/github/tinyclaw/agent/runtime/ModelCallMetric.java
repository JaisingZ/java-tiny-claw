package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.provider.ModelUsage;
import java.util.Objects;

/**
 * 单次模型调用的可观测指标。
 */
public final class ModelCallMetric {

    private final DecisionPhase phase;
    private final String model;
    private final long durationMillis;
    private final boolean success;
    private final String failureReason;
    private final ModelUsage usage;
    private final boolean usageAvailable;

    public ModelCallMetric(DecisionPhase phase, String model, long durationMillis, boolean success,
            String failureReason, ModelUsage usage, boolean usageAvailable) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.model = model == null ? "" : model;
        this.durationMillis = Math.max(0L, durationMillis);
        this.success = success;
        this.failureReason = failureReason;
        this.usage = usage == null ? ModelUsage.empty() : usage;
        this.usageAvailable = usageAvailable;
    }

    public DecisionPhase phase() {
        return phase;
    }

    public DecisionPhase getPhase() {
        return phase();
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model();
    }

    public long durationMillis() {
        return durationMillis;
    }

    public long getDurationMillis() {
        return durationMillis();
    }

    public boolean success() {
        return success;
    }

    public boolean isSuccess() {
        return success();
    }

    public String failureReason() {
        return failureReason;
    }

    public String getFailureReason() {
        return failureReason();
    }

    public ModelUsage usage() {
        return usage;
    }

    public ModelUsage getUsage() {
        return usage();
    }

    public boolean usageAvailable() {
        return usageAvailable;
    }

    public boolean isUsageAvailable() {
        return usageAvailable();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModelCallMetric)) {
            return false;
        }
        ModelCallMetric that = (ModelCallMetric) other;
        return durationMillis == that.durationMillis
                && success == that.success
                && usageAvailable == that.usageAvailable
                && phase == that.phase
                && Objects.equals(model, that.model)
                && Objects.equals(failureReason, that.failureReason)
                && Objects.equals(usage, that.usage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phase, model, durationMillis, success, failureReason, usage, usageAvailable);
    }
}
