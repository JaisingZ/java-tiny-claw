package com.jaising.agent.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllowListMiddlewareTest {

  @Test
  void deniesToolOutsideAllowList() {
    Set<String> allowed = new HashSet<String>(Arrays.asList("read", "write", "bash"));
    AllowListMiddleware middleware = new AllowListMiddleware(allowed);

    com.jaising.agent.middleware.MiddlewareDecision decision = middleware.beforeTool(
        AgentState.create(new Task("task-1", "risk check")),
        new ToolCall("rm", Collections.singletonMap("path", "/tmp/data")));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).contains("rm");
  }
}
