package io.github.tinyclaw.agent.provider;

import io.github.tinyclaw.agent.domain.Decision;
import java.util.Objects;

/**
 * 模型决策及其可观测元数据。
 */
public final class ModelResponse {

    private final Decision decision;
    private final ModelUsage usage;
    private final String model;
    private final boolean usageAvailable;

    public ModelResponse(Decision decision, ModelUsage usage, String model, boolean usageAvailable) {
        this.decision = Objects.requireNonNull(decision, "decision");
        this.usage = usage == null ? ModelUsage.empty() : usage;
        this.model = model == null ? "" : model;
        this.usageAvailable = usageAvailable;
    }

    public static ModelResponse of(Decision decision) {
        return new ModelResponse(decision, ModelUsage.empty(), "", false);
    }

    public Decision decision() {
        return decision;
    }

    public Decision getDecision() {
        return decision();
    }

    public ModelUsage usage() {
        return usage;
    }

    public ModelUsage getUsage() {
        return usage();
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model();
    }

    public boolean usageAvailable() {
        return usageAvailable;
    }

    public boolean isUsageAvailable() {
        return usageAvailable();
    }
}
