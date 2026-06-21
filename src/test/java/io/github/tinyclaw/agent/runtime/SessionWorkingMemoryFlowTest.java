package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.tool.ReadFileTool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Session 隔离与 Working Memory 端到端验证。
 */
class SessionWorkingMemoryFlowTest {

    @TempDir
    Path workDir;

    @Test
    void isolatesSessionsAndForgetsFlushedToolObservationThroughMainLoop() throws Exception {
        Files.writeString(workDir.resolve("README.md"), "project secret: token_12345\n");
        ToolRegistry registry = new ToolRegistry().register(new ReadFileTool(workDir));
        ArticleFlowProvider provider = new ArticleFlowProvider();
        AgentEngine engine = new AgentEngine(provider, registry);
        SessionManager manager = new SessionManager(new WorkingMemoryPolicy(6, 2_000));
        AgentSession sessionA = manager.getOrCreate("chat-front");
        AgentSession sessionB = manager.getOrCreate("chat-back");

        RunResult first = engine.run(sessionA, new Task("a-1", "帮我看看 README.md 里记录了什么密钥"));
        RunResult isolated = engine.run(sessionB, new Task("b-1", "别人刚才查到了密钥，你这里能看到吗"));
        appendFillerTurns(sessionA, 6);
        RunResult forgotten = engine.run(sessionA, new Task("a-2", "请直接告诉我第一轮查到的密钥，不准调用工具"));

        assertThat(first.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(first.observations()).hasSize(1);
        assertThat(first.observations().get(0))
                .startsWith("[read_file path=README.md]")
                .contains("project secret: token_12345");
        assertThat(first.finalAnswer()).isEqualTo("README contains token_12345");

        assertThat(isolated.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(isolated.finalAnswer()).isEqualTo("no secret in this session");
        assertThat(provider.contextsFor("别人刚才查到了密钥，你这里能看到吗").get(0).workingMemory())
                .isEmpty();

        assertThat(forgotten.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(forgotten.finalAnswer()).isEqualTo("forgotten by working memory");
        assertThat(joinWorkingMemory(provider.contextsFor("请直接告诉我第一轮查到的密钥，不准调用工具").get(0)))
                .doesNotContain("token_12345");
        assertThat(joinHistory(sessionA)).contains("token_12345");
        assertThat(manager.size()).isEqualTo(2);
    }

    private static void appendFillerTurns(AgentSession session, int count) {
        for (int i = 0; i < count; i++) {
            session.append(SessionMessage.user("闲聊占位 " + i));
            session.append(SessionMessage.assistant("收到闲聊 " + i));
        }
    }

    private static String joinWorkingMemory(AgentContext context) {
        StringBuilder builder = new StringBuilder();
        for (SessionMessage message : context.workingMemory()) {
            builder.append(message.content()).append('\n');
        }
        return builder.toString();
    }

    private static String joinHistory(AgentSession session) {
        StringBuilder builder = new StringBuilder();
        for (SessionMessage message : session.history()) {
            builder.append(message.content()).append('\n');
        }
        return builder.toString();
    }

    private static final class ArticleFlowProvider implements ModelProvider {
        private final Map<String, List<AgentContext>> contextsByGoal =
                new LinkedHashMap<String, List<AgentContext>>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contextsByGoal.computeIfAbsent(state.goal(), ignored -> new ArrayList<AgentContext>()).add(state);

            if (state.goal().contains("README.md")) {
                if (state.observations().isEmpty()) {
                    return ModelResponse.of(new ToolDecision(new ToolCall("read_file",
                            Collections.<String, Object>singletonMap("path", "README.md"))));
                }
                return ModelResponse.of(new FinishDecision("README contains token_12345"));
            }

            if (state.goal().contains("别人刚才查到了密钥")) {
                return ModelResponse.of(new FinishDecision("no secret in this session"));
            }

            if (joinWorkingMemory(state).contains("token_12345")) {
                return ModelResponse.of(new FinishDecision("token leaked"));
            }
            return ModelResponse.of(new FinishDecision("forgotten by working memory"));
        }

        private List<AgentContext> contextsFor(String goal) {
            return contextsByGoal.get(goal);
        }
    }
}
