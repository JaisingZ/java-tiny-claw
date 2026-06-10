package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ContextCompactionPolicy 参数与边界行为测试。
 */
class ContextCompactionPolicyTest {

    @Test
    void hasExpectedDefaultValues() {
        ContextCompactionPolicy policy = new ContextCompactionPolicy();

        assertThat(policy.maxContextChars()).isEqualTo(12_000);
        assertThat(policy.retainRecentMessages()).isEqualTo(6);
        assertThat(policy.maxObservationChars()).isEqualTo(1_000);
        assertThat(policy.headChars()).isEqualTo(500);
        assertThat(policy.tailChars()).isEqualTo(500);
        assertThat(policy.maskThresholdChars()).isEqualTo(200);

        assertThat(policy.getMaxContextChars()).isEqualTo(12_000);
        assertThat(policy.getRetainRecentMessages()).isEqualTo(6);
        assertThat(policy.getMaxObservationChars()).isEqualTo(1_000);
        assertThat(policy.getHeadChars()).isEqualTo(500);
        assertThat(policy.getTailChars()).isEqualTo(500);
        assertThat(policy.getMaskThresholdChars()).isEqualTo(200);
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThatThrownBy(() -> new ContextCompactionPolicy(0, 1, 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 0, 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 1, 0, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 1, 1, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 1, 1, 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 1, 1, 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHeadAndTailExceedingMaxObservationChars() {
        assertThatThrownBy(() -> new ContextCompactionPolicy(1, 1, 8, 5, 4, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
