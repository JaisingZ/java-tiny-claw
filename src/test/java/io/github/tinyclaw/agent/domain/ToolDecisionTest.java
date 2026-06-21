package io.github.tinyclaw.agent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ToolDecisionTest {

    @Test
    void wrapsSingleToolCallAsImmutableCallList() {
        ToolCall call = new ToolCall("echo", Collections.<String, Object>singletonMap("text", "hello"));

        ToolDecision decision = new ToolDecision(call);

        assertThat(decision.call()).isEqualTo(call);
        assertThat(decision.getCall()).isEqualTo(call);
        assertThat(decision.calls()).containsExactly(call);
        assertThat(decision.getCalls()).containsExactly(call);
        assertThatThrownBy(() -> decision.calls().add(call))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void supportsMultipleToolCallsAndRejectsSingleCallAccessor() {
        ToolCall first = new ToolCall("read1", Collections.<String, Object>emptyMap());
        ToolCall second = new ToolCall("read2", Collections.<String, Object>emptyMap());

        ToolDecision decision = new ToolDecision(Arrays.asList(first, second));

        assertThat(decision.calls()).containsExactly(first, second);
        assertThatThrownBy(decision::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires exactly one tool call");
    }

    @Test
    void convertsNullCallListToEmptyDecision() {
        ToolDecision decision = new ToolDecision((java.util.List<ToolCall>) null);

        assertThat(decision.calls()).isEmpty();
    }
}
