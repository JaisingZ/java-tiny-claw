package com.jaising.agent.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentApplicationTest {

    @Test
    void debugRunOutputPrintsOnlyCompactResult() {
        RunResult result = new RunResult(AgentState.create(new Task("task-cli", "goal"))
                .observe("Hello，Java！")
                .finish("done"));
        List<TraceEvent> events = List.of(new TraceEvent(TraceEventType.FINISHED,
                "task-cli", 1, "answer=done", 0));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AgentApplication.printRunOutput(result, events, true,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("RESULT status=SUCCESS answer=done failure=null")
                .doesNotContain("TRACE")
                .doesNotContain("OBSERVATIONS")
                .doesNotContain("Hello，Java！");
    }

    @Test
    void normalRunOutputKeepsTraceAndObservations() {
        RunResult result = new RunResult(AgentState.create(new Task("task-cli", "goal"))
                .observe("Hello，Java！")
                .finish("done"));
        List<TraceEvent> events = List.of(new TraceEvent(TraceEventType.FINISHED,
                "task-cli", 1, "answer=done", 0));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AgentApplication.printRunOutput(result, events, false,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("TRACE")
                .contains("FINISHED step=1 durationMs=0 detail=answer=done")
                .contains("RESULT status=SUCCESS answer=done failure=null")
                .contains("OBSERVATIONS")
                .contains("Hello，Java！");
    }
}
