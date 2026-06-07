package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.SessionMessageKind;
import io.github.tinyclaw.agent.domain.Task;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ContextCompactor 行为测试。
 */
class ContextCompactorTest {

    @Test
    void returnsOriginalContextWhenWithinLimit() {
        ContextCompactor compactor = new ContextCompactor(new ContextCompactionPolicy(1_000, 6, 10, 3, 3, 5));
        AgentContext input = new AgentContext(
                new Task("task-id", "goal"),
                0,
                List.of("short-observation"),
                "think",
                List.of(SessionMessage.user("user"),
                        SessionMessage.assistant("assistant"),
                        SessionMessage.observation("obs")));

        AgentContext output = compactor.compact(input);

        assertThat(output).isSameAs(input);
    }

    @Test
    void truncatesCurrentObservationWhenContextExceedsLimit() {
        ContextCompactionPolicy policy = new ContextCompactionPolicy(20, 2, 8, 4, 4, 5);
        ContextCompactor compactor = new ContextCompactor(policy);
        String current = "HEAD-" + "x".repeat(12) + "-TAIL";
        AgentContext input = new AgentContext(
                new Task("task-id", "goal"),
                0,
                List.of(current),
                "thought",
                List.of(SessionMessage.user("u")));

        AgentContext output = compactor.compact(input);
        String compacted = output.observations().get(0);

        assertThat(output).isNotSameAs(input);
        assertThat(compacted).contains("内容过长");
        assertThat(compacted).contains(String.valueOf(current.length()));
        assertThat(compacted).startsWith("HEAD");
        assertThat(compacted).endsWith("TAIL");
        assertThat(compacted).doesNotContain("x".repeat(10));
    }

    @Test
    void masksRemoteObservationInWorkingMemory() {
        ContextCompactionPolicy policy = new ContextCompactionPolicy(20, 2, 9, 3, 3, 6);
        ContextCompactor compactor = new ContextCompactor(policy);

        List<SessionMessage> workingMemory = new ArrayList<>();
        workingMemory.add(SessionMessage.user("user"));
        workingMemory.add(SessionMessage.observation("old-observation-" + "x".repeat(20)));
        workingMemory.add(SessionMessage.assistant("assistant"));
        workingMemory.add(SessionMessage.observation("recent-observation-" + "x".repeat(20)));
        workingMemory.add(SessionMessage.assistant("final"));

        AgentContext input = new AgentContext(
                new Task("task-id", "goal"),
                0,
                List.of("ok"),
                null,
                List.copyOf(workingMemory));

        AgentContext output = compactor.compact(input);

        assertThat(output.workingMemory().get(0).kind()).isEqualTo(SessionMessageKind.USER);
        assertThat(output.workingMemory().get(0).content()).isEqualTo("user");
        assertThat(output.workingMemory().get(1).content())
                .contains("早期工具输出已被压缩")
                .contains(String.valueOf(workingMemory.get(1).content().length()))
                .doesNotContain("x".repeat(6));
        assertThat(output.workingMemory().get(3).content())
                .contains("内容过长")
                .contains(String.valueOf(workingMemory.get(3).content().length()));
    }

    @Test
    void doesNotMutateOriginalContextOrSourceMessages() {
        ContextCompactor compactor = new ContextCompactor(new ContextCompactionPolicy(20, 1, 8, 4, 4, 5));
        String observation = "HEAD-" + "x".repeat(12) + "-TAIL";
        List<SessionMessage> workingMemory = new ArrayList<>();
        workingMemory.add(SessionMessage.user("user"));
        workingMemory.add(SessionMessage.observation("old-" + "y".repeat(20)));
        workingMemory.add(SessionMessage.assistant("assistant"));
        AgentContext input = new AgentContext(
                new Task("task-id", "goal"),
                0,
                List.of(observation),
                "thought",
                List.copyOf(workingMemory));

        AgentContext output = compactor.compact(input);

        assertThat(input.observations().get(0)).isEqualTo(observation);
        assertThat(input.workingMemory().get(1).content()).isEqualTo("old-" + "y".repeat(20));
        assertThat(output.workingMemory().get(1).content()).contains("早期工具输出已被压缩");
        assertThat(output).isNotSameAs(input);
    }
}
