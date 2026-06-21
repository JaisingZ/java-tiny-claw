package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.Decision;

/**
 * Provider 调用结果及耗时。
 */
final class ProviderCallResult {

    private final Decision decision;
    private final long durationMillis;

    ProviderCallResult(Decision decision, long durationMillis) {
        this.decision = decision;
        this.durationMillis = durationMillis;
    }

    Decision decision() {
        return decision;
    }

    long durationMillis() {
        return durationMillis;
    }
}
