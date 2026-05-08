package com.jaising.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.middleware.AllowAllMiddleware;
import com.jaising.agent.middleware.AllowListMiddleware;
import com.jaising.agent.provider.ModelProvider;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEventType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class AgentEngineTest {

  @Test
  void runsToolThenFinishes() {
    InMemoryStateStore store = new InMemoryStateStore();
    InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
    ToolRegistry registry = new ToolRegistry();
    registry.register(new EchoTool());

    ScriptedProvider provider = new ScriptedProvider();
    AgentEngine engine = new AgentEngine(provider, registry,
        Arrays.asList(new AllowAllMiddleware()), store, trace, 4);

    com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-1", "echo once"));

    assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
    assertThat(result.state().finalAnswer()).isEqualTo("done");
    assertThat(result.state().observations()).containsExactly("hello");
    assertThat(store.load("task-1")).isPresent();
    assertThat(store.load("task-1").orElseThrow().status()).isEqualTo(AgentStatus.SUCCESS);
    assertThat(trace.events()).extracting(event -> event.type()).contains(
        TraceEventType.MODEL_REQUEST,
        TraceEventType.TOOL_CALL,
        TraceEventType.TOOL_RESULT,
        TraceEventType.MODEL_REQUEST,
        TraceEventType.FINISHED);
  }

  @Test
  void failsWhenToolIsMissing() {
    InMemoryStateStore store = new InMemoryStateStore();
    InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
    ToolRegistry registry = new ToolRegistry();

    AgentEngine engine = new AgentEngine(new MissingToolProvider(), registry,
        Arrays.asList(new AllowAllMiddleware()), store, trace, 4);

    com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-2", "missing tool"));

    assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
    assertThat(result.state().failureReason()).isEqualTo("Unknown tool: missing");
  }

  @Test
  void failsWhenMiddlewareRejectsTool() {
    InMemoryStateStore store = new InMemoryStateStore();
    InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
    ToolRegistry registry = new ToolRegistry();
    registry.register(new EchoTool());

    AgentEngine engine = new AgentEngine(new ToolOnlyProvider(), registry,
        Arrays.asList(new AllowListMiddleware(new HashSet<String>(Collections.singleton("noop")))),
        store, trace, 4);

    com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-3", "blocked"));

    assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
    assertThat(result.state().failureReason()).isEqualTo("Tool not allowed: echo");
  }

  @Test
  void failsWhenMaxStepsIsExceeded() {
    InMemoryStateStore store = new InMemoryStateStore();
    InMemoryTraceRecorder trace = new InMemoryTraceRecorder();
    ToolRegistry registry = new ToolRegistry();
    registry.register(new EchoTool());

    AgentEngine engine = new AgentEngine(new ToolOnlyProvider(), registry,
        Arrays.asList(new AllowAllMiddleware()), store, trace, 1);

    com.jaising.agent.runtime.RunResult result = engine.run(new Task("task-4", "loop forever"));

    assertThat(result.state().status()).isEqualTo(AgentStatus.FAILED);
    assertThat(result.state().failureReason()).isEqualTo("max_steps_exceeded");
    assertThat(result.state().stepCount()).isEqualTo(1);
  }

  private static final class ScriptedProvider implements ModelProvider {
    @Override
    public Decision decide(AgentState state) {
      if (state.observations().isEmpty()) {
        return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
      }
      return new FinishDecision("done");
    }
  }

  private static final class MissingToolProvider implements ModelProvider {
    @Override
    public Decision decide(AgentState state) {
      return new ToolDecision(new ToolCall("missing", Collections.<String, Object>emptyMap()));
    }
  }

  private static final class ToolOnlyProvider implements ModelProvider {
    @Override
    public Decision decide(AgentState state) {
      return new ToolDecision(new ToolCall("echo", Collections.singletonMap("text", "hello")));
    }
  }

  private static final class EchoTool implements Tool {
    @Override
    public String name() {
      return "echo";
    }

    @Override
    public ToolResult execute(ToolCall call, AgentState state) {
      return ToolResult.success(String.valueOf(call.arguments().get("text")));
    }
  }
}
