package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.context.PromptComposer;
import io.github.tinyclaw.agent.context.PromptContext;
import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ParallelToolDecision;
import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.SessionMessageKind;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.provider.ModelProvider;
import io.github.tinyclaw.agent.provider.ModelResponse;
import io.github.tinyclaw.agent.provider.ModelUsage;
import io.github.tinyclaw.agent.tool.SubagentTool;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/**
 * 主循环测试
 * 覆盖成功和关键失败路径
 */
class AgentEngineTest {

    /**
     * 工具执行后正常结束
     */
    @Test
    void runsToolThenFinishes() {
        EngineFixture fixture = fixture().withTools(new EchoTool());

        RunResult result = fixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-1", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.finalAnswer()).isEqualTo("done");
        assertThat(result.observations()).containsExactly("hello");
    }

    @Test
    void recordsModelUsageAndToolMetricsForRun() {
        EngineFixture fixture = fixture().withTools(new EchoTool());

        RunResult result = fixture.run(scriptedProvider(
                response(tool("echo", "text", "hello"), "model-a", 11, 2, 13),
                response(finish("done"), "model-a", 17, 5, 22)), "task-metrics", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.metrics().modelCalls()).hasSize(2);
        assertThat(result.metrics().modelCallCount()).isEqualTo(2);
        assertThat(result.metrics().promptTokens()).isEqualTo(28);
        assertThat(result.metrics().completionTokens()).isEqualTo(7);
        assertThat(result.metrics().totalTokens()).isEqualTo(35);
        assertThat(result.metrics().usageUnavailableCount()).isZero();
        assertThat(result.metrics().modelDurationMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(result.metrics().toolCalls()).hasSize(1);
        assertThat(result.metrics().toolCalls().get(0).toolName()).isEqualTo("echo");
        assertThat(result.metrics().toolCalls().get(0).success()).isTrue();
        assertThat(result.metrics().toolCalls().get(0).outputBytes()).isEqualTo(5);
        assertThat(result.metrics().toolDurationMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void recordsProviderFailureMetric() {
        EngineFixture fixture = fixture();

        RunResult result = fixture.run((state, phase, tools, systemPrompt) -> {
            throw new RuntimeException("boom");
        }, "task-provider-metric", "fail");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.metrics().modelCalls()).hasSize(1);
        assertThat(result.metrics().modelCalls().get(0).phase()).isEqualTo(DecisionPhase.ACTION);
        assertThat(result.metrics().modelCalls().get(0).success()).isFalse();
        assertThat(result.metrics().modelCalls().get(0).failureReason()).isEqualTo("provider_error: boom");
        assertThat(result.metrics().usageUnavailableCount()).isEqualTo(1);
    }

    @Test
    void recordsMetricsIntoAgentSessionAfterRun() {
        EngineFixture fixture = fixture().withTools(new EchoTool());
        AgentSession session = new AgentSession("chat-metrics");

        RunResult result = fixture.run(scriptedProvider(
                response(tool("echo", "text", "hello"), "model-a", 10, 4, 14),
                response(finish("done"), "model-a", 20, 6, 26)),
                session, "task-session-metrics", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(session.metrics().modelCallCount()).isEqualTo(2);
        assertThat(session.metrics().promptTokens()).isEqualTo(30);
        assertThat(session.metrics().completionTokens()).isEqualTo(10);
        assertThat(session.metrics().totalTokens()).isEqualTo(40);
        assertThat(session.metrics().toolCallCount()).isEqualTo(1);
    }

    /**
     * 关闭慢思考时保持单阶段循环
     */
    @Test
    void runsSingleStageLoopWhenThinkingIsDisabled() {
        EngineFixture fixture = fixture().withTools(new EchoTool());

        RunResult result = fixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-1-disabled", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly("hello");
    }

    /**
     * 找不到工具时作为可恢复观测进入下一轮，最终由步数兜底。
     */
    @Test
    void recordsMissingToolAsRecoveryObservationUntilMaxSteps() {
        EngineFixture fixture = fixture().withMaxSteps(1);

        RunResult result = fixture.run(constantProvider(tool("missing")), "task-2", "missing tool");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("max_steps_exceeded");
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0))
                .contains("Error executing missing: Unknown tool: missing")
                .contains("[Recovery Hint]")
                .contains("available tools");
    }

    /**
     * 工具返回失败时写入自愈观测，让模型下一轮修正并完成。
     */
    @Test
    void recoversWhenToolReturnsFailureAndProviderCorrectsNextTurn() {
        EngineFixture fixture = fixture().withTools(new FailingTool());
        RecoveringProvider provider = new RecoveringProvider();

        RunResult result = fixture.run(provider, "task-tool-fails", "tool fails");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.finalAnswer()).isEqualTo("recovered");
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0))
                .contains("Error executing fail_tool: read failed")
                .doesNotContain("[Recovery Hint]");
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(1).observations()).containsExactly(result.observations().get(0));
    }

    /**
     * 达到步数上限时失败
     */
    @Test
    void failsWhenMaxStepsIsExceeded() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withMaxSteps(1);

        RunResult result = fixture.run(constantProvider(tool("echo", "text", "hello")), "task-4", "loop forever");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("max_steps_exceeded");
        assertThat(result.stepCount()).isEqualTo(1);
    }

    /**
     * 开启慢思考时 每轮先思考 再行动
     */
    @Test
    void runsThinkingBeforeActionWhenEnabled() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true);
        ThinkingScriptedProvider provider = new ThinkingScriptedProvider();

        RunResult result = fixture.run(provider, "task-5", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.finalAnswer()).isEqualTo("done");
        assertThat(result.observations()).containsExactly("hello");
        assertThat(provider.phases()).containsExactly(
                DecisionPhase.THINKING,
                DecisionPhase.ACTION,
                DecisionPhase.THINKING,
                DecisionPhase.ACTION);
        assertThat(provider.toolsByPhase().get(0)).isEmpty();
        assertThat(provider.toolsByPhase().get(1)).containsExactly(new ToolDefinition(
                "echo",
                "echo",
                Collections.<String, Object>singletonMap("type", "object")));
    }

    /**
     * 每次调用模型前由 Runtime 组装 System Prompt 并传给 Provider
     */
    @Test
    void passesComposedSystemPromptToProvider() {
        RecordingPromptComposer composer = new RecordingPromptComposer("composed-system-prompt");
        RecordingPromptProvider provider = new RecordingPromptProvider(finish("done"));
        EngineFixture fixture = fixture().withPromptComposer(composer);

        RunResult result = fixture.run(provider, "task-prompt", "finish");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(composer.contexts()).hasSize(1);
        assertThat(composer.contexts().get(0).phase()).isEqualTo(DecisionPhase.ACTION);
        assertThat(provider.prompts()).containsExactly("composed-system-prompt");
    }

    /**
     * 可读运行日志按主循环顺序输出关键节点
     */
    @Test
    void emitsReadableRunLoggerEventsInLoopOrder() {
        RecordingRunLogger runLogger = new RecordingRunLogger();
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true).withRunLogger(runLogger);

        RunResult result = fixture.run(new ThinkingScriptedProvider(), "task-readable-log", "echo once");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(runLogger.events()).containsSubsequence(
                "turn:1",
                "thinking-start",
                "thinking-complete:plan to call echo",
                "action-start:[echo]",
                "tool-decision:echo",
                "tool-start:echo",
                "tool-success:echo",
                "turn:2",
                "thinking-start",
                "thinking-complete:plan to finish",
                "action-start:[echo]",
                "finish:done");
    }

    /**
     * 慢思考阶段模型异常直接失败
     */
    @Test
    void failsWhenThinkingProviderThrows() {
        EngineFixture fixture = fixture().withThinking(true);

        RunResult result = fixture.run((state, phase, tools, systemPrompt) -> {
            if (phase == DecisionPhase.THINKING) {
                throw new RuntimeException("boom");
            }
            return response(finish("unused"));
        }, "task-6", "think fails");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("provider_error: boom");
    }

    /**
     * 慢思考阶段不允许直接调用工具
     */
    @Test
    void failsWhenThinkingReturnsToolDecision() {
        EngineFixture fixture = fixture().withTools(new EchoTool()).withThinking(true);

        RunResult result = fixture.run(constantProvider(tool("echo", "text", "hello")), "task-7", "bad thinking");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("unsupported_thinking_decision");
        assertThat(result.observations()).isEmpty();
    }

    /**
     * 并行工具决策执行成功
     */
    @Test
    void runsParallelToolsInDeclaredOrder() {
        EngineFixture fixture = fixture()
                .withTools(new ReadOnlyEchoTool("read1", "hello"), new ReadOnlyEchoTool("read2", "world"));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("read2")),
                finish("done")), "task-parallel", "parallel echo");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly("hello\n\nworld");
    }

    /**
     * 并行决策中的未知工具保持与单工具相同的失败文案
     */
    @Test
    void failsWhenParallelDecisionContainsMissingTool() {
        EngineFixture fixture = fixture().withTools(new ReadOnlyEchoTool("read1", "hello"));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("missing"))), "task-parallel-missing", "parallel missing");

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("max_steps_exceeded");
        assertThat(result.observations()).hasSize(4);
        assertThat(result.observations().get(0))
                .contains("hello")
                .contains("Error executing missing: Unknown tool: missing");
    }

    /**
     * 并行工具中的失败也按声明顺序写入同一条观测，供下一轮恢复。
     */
    @Test
    void recordsMixedParallelSuccessAndFailureInDeclaredOrder() {
        EngineFixture fixture = fixture()
                .withTools(new ReadOnlyEchoTool("read1", "hello"), new FailingTool());
        RecordingContextProvider provider = new RecordingContextProvider(
                parallel(call("read1"), call("fail_tool")),
                finish("done"));

        RunResult result = fixture.run(provider, "task-parallel-recovery", "parallel recovery");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(1);
        assertThat(result.observations().get(0))
                .startsWith("hello")
                .contains("\n\nError executing fail_tool: read failed");
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(1).observations()).containsExactly(result.observations().get(0));
    }

    /**
     * 连续无效工具调用第三次后注入 System Reminder，供下一轮模型决策读取。
     */
    @Test
    void injectsSystemReminderAfterThreeRepeatedIneffectiveToolCalls() {
        EngineFixture fixture = fixture().withTools(new FailingTool()).withMaxSteps(5);
        ReminderAwareProvider provider = new ReminderAwareProvider();

        RunResult result = fixture.run(provider, "task-reminder", "avoid repeated failure");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.finalAnswer()).isEqualTo("stopped after reminder");
        assertThat(result.observations()).hasSize(3);
        assertThat(result.observations().get(2))
                .contains("Error executing fail_tool: read failed")
                .contains("[SYSTEM REMINDER]");
        assertThat(provider.contexts()).hasSize(4);
        assertThat(provider.contexts().get(3).observations().get(2)).contains("[SYSTEM REMINDER]");
    }

    /**
     * Reminder 追加在同一轮观测末尾。
     */
    @Test
    void appendsSystemReminderAtEndOfCurrentObservation() {
        EngineFixture fixture = fixture().withTools(new FailingTool()).withMaxSteps(4);
        RecordingContextProvider provider = new RecordingContextProvider(
                tool("fail_tool"),
                tool("fail_tool"),
                tool("fail_tool"),
                finish("done"));

        RunResult result = fixture.run(provider, "task-reminder-order", "repeat same failed tool");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(3);
        assertThat(result.observations().get(2))
                .startsWith("Error executing fail_tool: read failed")
                .endsWith("explain what manual input is needed.");
    }

    /**
     * 并行工具多次无效时，每轮最多追加一条 Reminder。
     */
    @Test
    void appendsAtMostOneSystemReminderForParallelFailuresPerTurn() {
        EngineFixture fixture = fixture()
                .withTools(new FailingTool(), new FailingTool("fail_tool_2"))
                .withMaxSteps(4);
        RecordingContextProvider provider = new RecordingContextProvider(
                parallel(call("fail_tool"), call("fail_tool_2")),
                parallel(call("fail_tool"), call("fail_tool_2")),
                parallel(call("fail_tool"), call("fail_tool_2")),
                finish("done"));

        RunResult result = fixture.run(provider, "task-parallel-reminder", "repeat parallel failures");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).hasSize(3);
        assertThat(countOccurrences(result.observations().get(2), "[SYSTEM REMINDER]")).isEqualTo(1);
        assertThat(result.observations().get(2))
                .contains("Error executing fail_tool: read failed")
                .contains("Error executing fail_tool_2: read failed")
                .contains("[SYSTEM REMINDER]");
    }

    /**
     * 空并行决策也会推进一步
     */
    @Test
    void advancesWhenParallelDecisionIsEmpty() {
        EngineFixture fixture = fixture();

        RunResult result = fixture.run(scriptedProvider(
                parallel(),
                finish("done")), "task-parallel-empty", "parallel empty");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.stepCount()).isEqualTo(1);
        assertThat(result.observations()).isEmpty();
    }

    /**
     * 混合只读和副作用工具时 结果顺序仍以决策声明顺序为准
     */
    @Test
    void keepsDeclaredOutputOrderForMixedParallelTools() {
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<String>());
        EngineFixture fixture = fixture().withTools(
                new TrackingTool("read1", "hello", false, executionOrder),
                new TrackingTool("write1", "write-a", true, executionOrder),
                new TrackingTool("read2", "world", false, executionOrder),
                new TrackingTool("write2", "write-b", true, executionOrder));

        RunResult result = fixture.run(scriptedProvider(
                parallel(call("read1"), call("write1"), call("read2"), call("write2")),
                finish("done")), "task-parallel-mixed", "parallel mixed");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly("hello\n\nwrite-a\n\nworld\n\nwrite-b");
        assertThat(executionOrder).containsSubsequence("write1", "write2");
    }

    /**
     * 单工具和并行工具都只推进一步并追加一条观测
     */
    @Test
    void keepsStepAndObservationSemanticsForSingleAndParallelTools() {
        EngineFixture singleFixture = fixture().withTools(new EchoTool());
        RunResult singleResult = singleFixture.run(scriptedProvider(
                tool("echo", "text", "hello"),
                finish("done")), "task-single-shape", "single shape");

        EngineFixture parallelFixture = fixture()
                .withTools(new ReadOnlyEchoTool("read1", "hello"), new ReadOnlyEchoTool("read2", "world"));
        RunResult parallelResult = parallelFixture.run(scriptedProvider(
                parallel(call("read1"), call("read2")),
                finish("done")), "task-parallel-shape", "parallel shape");

        assertThat(singleResult.stepCount()).isEqualTo(1);
        assertThat(singleResult.observations()).hasSize(1);
        assertThat(parallelResult.stepCount()).isEqualTo(1);
        assertThat(parallelResult.observations()).hasSize(1);
    }

    /**
     * 同一 Session 的后续任务可以看到前一次最终回答。
     */
    @Test
    void runWithSessionCarriesPreviousAnswerIntoNextTask() {
        RecordingContextProvider provider = new RecordingContextProvider(finish("first-answer"), finish("second-answer"));
        EngineFixture fixture = fixture();
        AgentSession session = new AgentSession("chat-a");

        RunResult first = fixture.run(provider, session, "task-session-1", "first question");
        RunResult second = fixture.run(provider, session, "task-session-2", "second question");

        assertThat(first.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(0).workingMemory()).isEmpty();
        assertThat(provider.contexts().get(1).workingMemory())
                .containsExactly(
                        SessionMessage.user("first question"),
                        SessionMessage.assistant("first-answer"));
    }

    @Test
    void spawnSubagentReturnsOnlySummaryToParentLoop() {
        RecordingContextProvider provider = new RecordingContextProvider(
                tool("spawn_subagent", "task_prompt", "inspect auth"),
                finish("done"));
        EngineFixture fixture = fixture().withTools(
                new SubagentTool(prompt -> ToolResult.success("condensed auth report")));

        RunResult result = fixture.run(provider, "task-subagent-summary", "delegate exploration");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly("【子智能体探索报告】\ncondensed auth report");
        assertThat(provider.contexts()).hasSize(2);
        assertThat(provider.contexts().get(1).observations())
                .containsExactly("【子智能体探索报告】\ncondensed auth report");
    }

    @Test
    void defaultSubagentRunnerStartsWithCleanWorkingMemory() {
        RecordingContextProvider provider = new RecordingContextProvider(finish("child summary"));
        DefaultSubagentRunner runner = new DefaultSubagentRunner(provider, Path.of("."));
        AgentSession parentSession = new AgentSession("parent-session");
        parentSession.append(SessionMessage.user("previous parent task"));
        parentSession.append(SessionMessage.assistant("previous parent answer"));

        ToolResult summary = runner.run("inspect child task");

        assertThat(summary).isEqualTo(ToolResult.success("child summary"));
        assertThat(provider.contexts()).hasSize(1);
        assertThat(provider.contexts().get(0).goal()).isEqualTo("inspect child task");
        assertThat(provider.contexts().get(0).workingMemory()).isEmpty();
    }

    @Test
    void defaultSubagentRunnerExposesReadFileAndBashOnly() {
        RecordingSubagentPromptProvider provider = new RecordingSubagentPromptProvider();
        DefaultSubagentRunner runner = new DefaultSubagentRunner(provider, Path.of("."),
                context -> "base prompt");

        ToolResult summary = runner.run("inspect with shell");

        assertThat(summary).isEqualTo(ToolResult.success("child summary"));
        assertThat(provider.toolNames()).containsExactly("read_file", "bash");
        assertThat(provider.prompts()).hasSize(1);
        assertThat(provider.prompts().get(0))
                .contains("read_file")
                .contains("bash")
                .contains("不要写文件")
                .doesNotContain("不要请求 write_file、edit_file、bash 或 spawn_subagent");
    }

    @Test
    void defaultSubagentRunnerFailsWhenMaxStepsExceeded() {
        DefaultSubagentRunner runner = new DefaultSubagentRunner(constantProvider(tool("missing")), Path.of("."));

        ToolResult result = runner.run("never finishes");

        assertThat(result).isEqualTo(ToolResult.failure("max_steps_exceeded"));
    }

    @Test
    void runsParallelSpawnSubagentCallsInDeclaredOrder() {
        SubagentTool tool = new SubagentTool(prompt -> ToolResult.success("summary-" + prompt));
        EngineFixture fixture = fixture().withTools(tool);

        RunResult result = fixture.run(scriptedProvider(
                parallel(
                        call("spawn_subagent", "task_prompt", "a"),
                        call("spawn_subagent", "task_prompt", "b")),
                finish("done")), "task-parallel-subagent", "parallel subagents");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly(
                "【子智能体探索报告】\nsummary-a\n\n【子智能体探索报告】\nsummary-b");
    }

    /**
     * 不同 Session 的历史互不泄漏。
     */
    @Test
    void runWithDifferentSessionsKeepsHistoryIsolated() {
        RecordingContextProvider provider = new RecordingContextProvider(
                finish("answer-a"),
                finish("answer-b"),
                finish("answer-a2"));
        EngineFixture fixture = fixture();
        AgentSession sessionA = new AgentSession("chat-a");
        AgentSession sessionB = new AgentSession("chat-b");

        fixture.run(provider, sessionA, "task-a1", "question a1");
        fixture.run(provider, sessionB, "task-b1", "question b1");
        fixture.run(provider, sessionA, "task-a2", "question a2");

        assertThat(provider.contexts()).hasSize(3);
        assertThat(provider.contexts().get(1).workingMemory()).isEmpty();
        assertThat(provider.contexts().get(2).workingMemory())
                .containsExactly(
                        SessionMessage.user("question a1"),
                        SessionMessage.assistant("answer-a"));
    }

    /**
     * Provider 调用前应压缩超长 observation，但 RunResult 仍保留真实工具输出。
     */
    @Test
    void compactsOversizedObservationBeforeNextProviderCall() {
        String longOutput = "HEAD-" + repeat("x", 1_200) + "-TAIL";
        RecordingContextProvider provider = new RecordingContextProvider(
                tool("echo", "text", longOutput),
                finish("done"));
        ContextCompactionPolicy policy = new ContextCompactionPolicy(200, 2, 100, 20, 20, 50);
        EngineFixture fixture = fixture()
                .withTools(new EchoTool())
                .withContextCompactor(new ContextCompactor(policy));

        RunResult result = fixture.run(provider, "task-compact-observation", "read huge output");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).containsExactly(longOutput);
        assertThat(provider.contexts()).hasSize(2);
        String providerObservation = provider.contexts().get(1).observations().get(0);
        assertThat(providerObservation).contains("HEAD-");
        assertThat(providerObservation).contains("-TAIL");
        assertThat(providerObservation).contains("内容过长");
        assertThat(providerObservation).doesNotContain(repeat("x", 200));
    }

    /**
     * Session 历史保存原始输出，压缩只影响本次 Provider 输入。
     */
    @Test
    void compactionDoesNotMutateSessionHistory() {
        String longOutput = "RAW-" + repeat("o", 1_200) + "-END";
        RecordingContextProvider provider = new RecordingContextProvider(
                tool("echo", "text", longOutput),
                finish("done"));
        ContextCompactionPolicy policy = new ContextCompactionPolicy(200, 2, 100, 20, 20, 50);
        EngineFixture fixture = fixture()
                .withTools(new EchoTool())
                .withContextCompactor(new ContextCompactor(policy));
        AgentSession session = new AgentSession("chat-compaction");

        RunResult result = fixture.run(provider, session, "task-session-compaction", "read huge output");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(session.history())
                .containsExactly(
                        SessionMessage.user("read huge output"),
                        SessionMessage.observation(longOutput),
                        SessionMessage.assistant("done"));
    }

    /**
     * Session 历史应保存自愈后的错误观测，供后续任务使用。
     */
    @Test
    void sessionHistoryStoresRecoveryObservation() {
        RecoveringProvider provider = new RecoveringProvider();
        EngineFixture fixture = fixture().withTools(new FailingTool());
        AgentSession session = new AgentSession("chat-recovery");

        RunResult result = fixture.run(provider, session, "task-session-recovery", "recover from tool failure");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(session.history())
                .containsExactly(
                        SessionMessage.user("recover from tool failure"),
                        SessionMessage.observation(result.observations().get(0)),
                        SessionMessage.assistant("recovered"));
    }

    /**
     * Session 历史应保存包含 System Reminder 的观测。
     */
    @Test
    void sessionHistoryStoresSystemReminderObservation() {
        ReminderAwareProvider provider = new ReminderAwareProvider();
        EngineFixture fixture = fixture().withTools(new FailingTool()).withMaxSteps(5);
        AgentSession session = new AgentSession("chat-system-reminder");

        RunResult result = fixture.run(provider, session, "task-session-reminder", "repeat same failed tool");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(session.history())
                .contains(
                        SessionMessage.user("repeat same failed tool"),
                        SessionMessage.assistant("stopped after reminder"));
        assertThat(session.history().get(3).content()).contains("[SYSTEM REMINDER]");
    }

    /**
     * Thinking 和 Action 阶段都应使用压缩后的上下文。
     */
    @Test
    void compactsContextForThinkingAndActionPhases() {
        ContextPhaseRecordingProvider provider = new ContextPhaseRecordingProvider();
        ContextCompactionPolicy policy = new ContextCompactionPolicy(200, 2, 100, 20, 20, 50);
        EngineFixture fixture = fixture()
                .withThinking(true)
                .withContextCompactor(new ContextCompactor(policy));
        AgentSession session = new AgentSession("chat-thinking-compaction",
                new WorkingMemoryPolicy(10, 5_000));
        session.append(SessionMessage.user("previous question"));
        session.append(SessionMessage.observation("OBS-" + repeat("z", 1_200) + "-END"));

        RunResult result = fixture.run(provider, session, "task-thinking-compaction", "finish");

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(provider.phases()).containsExactly(DecisionPhase.THINKING, DecisionPhase.ACTION);
        assertThat(provider.contexts()).hasSize(2);
        for (AgentContext context : provider.contexts()) {
            SessionMessage compactedObservation = context.workingMemory().get(1);
            assertThat(compactedObservation.kind()).isEqualTo(SessionMessageKind.OBSERVATION);
            assertThat(compactedObservation.content())
                    .contains("内容过长")
                    .contains("OBS-")
                    .contains("-END")
                    .doesNotContain(repeat("z", 200));
        }
    }

    private EngineFixture fixture() {
        return new EngineFixture();
    }

    private static ModelResponse response(Decision decision, String model, int promptTokens,
            int completionTokens, int totalTokens) {
        return new ModelResponse(decision, new ModelUsage(promptTokens, completionTokens, totalTokens), model, true);
    }

    private static ModelResponse response(Decision decision) {
        return ModelResponse.of(decision);
    }

    private static ModelProvider scriptedProvider(final Decision... decisions) {
        ModelResponse[] responses = new ModelResponse[decisions.length];
        for (int i = 0; i < decisions.length; i++) {
            responses[i] = response(decisions[i]);
        }
        return scriptedProvider(responses);
    }

    private static ModelProvider scriptedProvider(final ModelResponse... responses) {
        return new ModelProvider() {
            private int index = 0;

            @Override
            public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                    String systemPrompt) {
                int current = index;
                if (current < responses.length - 1) {
                    index++;
                }
                return responses[current];
            }
        };
    }

    private static ModelProvider constantProvider(final Decision decision) {
        return (state, phase, availableTools, systemPrompt) -> response(decision);
    }

    private static ToolDecision tool(String toolName, Object... arguments) {
        return new ToolDecision(call(toolName, arguments));
    }

    private static FinishDecision finish(String answer) {
        return new FinishDecision(answer);
    }

    private static ParallelToolDecision parallel(ToolCall... calls) {
        return new ParallelToolDecision(Arrays.asList(calls));
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static int countOccurrences(String content, String text) {
        int count = 0;
        int index = 0;
        while (index >= 0) {
            index = content.indexOf(text, index);
            if (index >= 0) {
                count++;
                index += text.length();
            }
        }
        return count;
    }

    private static ToolCall call(String toolName, Object... arguments) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < arguments.length; i += 2) {
            values.put(String.valueOf(arguments[i]), arguments[i + 1]);
        }
        return new ToolCall(toolName, values);
    }

    /**
     * 按阶段返回思考 行动和结束
     */
    private static final class ThinkingScriptedProvider implements ModelProvider {
        private final List<DecisionPhase> phases = new ArrayList<DecisionPhase>();
        private final List<List<ToolDefinition>> toolsByPhase = new ArrayList<List<ToolDefinition>>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            phases.add(phase);
            toolsByPhase.add(availableTools);
            if (phase == DecisionPhase.THINKING) {
                if (state.observations().isEmpty()) {
                    return response(new ThinkingDecision("plan to call echo"));
                }
                return response(new ThinkingDecision("plan to finish"));
            }
            if (state.observations().isEmpty()) {
                return response(tool("echo", "text", "hello"));
            }
            return response(finish("done"));
        }

        List<DecisionPhase> phases() {
            return phases;
        }

        List<List<ToolDefinition>> toolsByPhase() {
            return toolsByPhase;
        }
    }

    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.success(String.valueOf(call.arguments().get("text")));
        }
    }

    private static final class ReadOnlyEchoTool implements Tool {
        private final String name;
        private final String output;

        private ReadOnlyEchoTool(String name, String output) {
            this.name = name;
            this.output = output;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.success(output);
        }

        @Override
        public boolean isSideEffect() {
            return false;
        }
    }

    private static final class TrackingTool implements Tool {
        private final String name;
        private final String output;
        private final boolean sideEffect;
        private final List<String> executionOrder;

        private TrackingTool(String name, String output, boolean sideEffect, List<String> executionOrder) {
            this.name = name;
            this.output = output;
            this.sideEffect = sideEffect;
            this.executionOrder = executionOrder;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            executionOrder.add(name);
            return ToolResult.success(output);
        }

        @Override
        public boolean isSideEffect() {
            return sideEffect;
        }
    }

    /**
     * 总是返回失败的工具
     */
    private static final class FailingTool implements Tool {
        private final String name;

        private FailingTool() {
            this("fail_tool");
        }

        private FailingTool(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ToolResult execute(ToolCall call, AgentContext state) {
            return ToolResult.failure("read failed");
        }
    }

    private static final class RecordingPromptComposer implements PromptComposer {
        private final String prompt;
        private final List<PromptContext> contexts = new ArrayList<PromptContext>();

        private RecordingPromptComposer(String prompt) {
            this.prompt = prompt;
        }

        @Override
        public String compose(PromptContext context) {
            contexts.add(context);
            return prompt;
        }

        private List<PromptContext> contexts() {
            return contexts;
        }
    }

    private static final class RecordingPromptProvider implements ModelProvider {
        private final Decision decision;
        private final List<String> prompts = new ArrayList<String>();

        private RecordingPromptProvider(Decision decision) {
            this.decision = decision;
        }

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            prompts.add(systemPrompt);
            return response(decision);
        }

        private List<String> prompts() {
            return prompts;
        }
    }

    private static final class RecordingContextProvider implements ModelProvider {
        private final Decision[] decisions;
        private final List<AgentContext> contexts = new ArrayList<AgentContext>();
        private int index;

        private RecordingContextProvider(Decision... decisions) {
            this.decisions = decisions;
        }

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contexts.add(state);
            int current = index;
            if (current < decisions.length - 1) {
                index++;
            }
            return response(decisions[current]);
        }

        private List<AgentContext> contexts() {
            return contexts;
        }
    }

    private static final class RecordingSubagentPromptProvider implements ModelProvider {
        private final List<String> prompts = new ArrayList<String>();
        private final List<String> toolNames = new ArrayList<String>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            prompts.add(systemPrompt);
            for (ToolDefinition tool : availableTools) {
                toolNames.add(tool.name());
            }
            return response(finish("child summary"));
        }

        private List<String> prompts() {
            return prompts;
        }

        private List<String> toolNames() {
            return toolNames;
        }
    }

    private static final class RecoveringProvider implements ModelProvider {
        private final List<AgentContext> contexts = new ArrayList<AgentContext>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contexts.add(state);
            if (state.observations().isEmpty()) {
                return response(tool("fail_tool"));
            }
            return response(finish("recovered"));
        }

        private List<AgentContext> contexts() {
            return contexts;
        }
    }

    private static final class ReminderAwareProvider implements ModelProvider {
        private final List<AgentContext> contexts = new ArrayList<AgentContext>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contexts.add(state);
            if (containsReminder(state.observations())) {
                return response(finish("stopped after reminder"));
            }
            return response(tool("fail_tool"));
        }

        private boolean containsReminder(List<String> observations) {
            for (String observation : observations) {
                if (observation.contains("[SYSTEM REMINDER]")) {
                    return true;
                }
            }
            return false;
        }

        private List<AgentContext> contexts() {
            return contexts;
        }
    }

    private static final class ContextPhaseRecordingProvider implements ModelProvider {
        private final List<DecisionPhase> phases = new ArrayList<DecisionPhase>();
        private final List<AgentContext> contexts = new ArrayList<AgentContext>();

        @Override
        public ModelResponse decide(AgentContext state, DecisionPhase phase, List<ToolDefinition> availableTools,
                String systemPrompt) {
            contexts.add(state);
            phases.add(phase);
            if (phase == DecisionPhase.THINKING) {
                return response(new ThinkingDecision("ready"));
            }
            return response(finish("done"));
        }

        private List<DecisionPhase> phases() {
            return phases;
        }

        private List<AgentContext> contexts() {
            return contexts;
        }
    }

    private static class RunLoggerAdapter implements RunLogger {
        @Override
        public void writeLine(String line) {
        }

        @Override
        public void writeBlankLine() {
        }

        @Override
        public void registryMounted(String toolName) {
        }

        @Override
        public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
                List<ToolDefinition> tools) {
        }

        @Override
        public void turnStarted(int turn) {
        }

        @Override
        public void thinkingStarted() {
        }

        @Override
        public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        }

        @Override
        public void actionStarted(List<ToolDefinition> tools) {
        }

        @Override
        public void toolDecision(ToolDecision decision) {
        }

        @Override
        public void toolStarted(ToolCall call) {
        }

        @Override
        public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        }

        @Override
        public void finished(FinishDecision decision) {
        }

        @Override
        public void failed(String reason) {
        }
    }

    /**
     * 记录运行日志事件
     */
    private static final class RecordingRunLogger extends RunLoggerAdapter {
        private final List<String> events = new ArrayList<String>();

        @Override
        public void turnStarted(int turn) {
            events.add("turn:" + turn);
        }

        @Override
        public void thinkingStarted() {
            events.add("thinking-start");
        }

        @Override
        public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
            events.add("thinking-complete:" + decision.thought());
        }

        @Override
        public void actionStarted(List<ToolDefinition> tools) {
            events.add("action-start:" + toolNames(tools));
        }

        @Override
        public void toolDecision(ToolDecision decision) {
            events.add("tool-decision:" + decision.call().toolName());
        }

        @Override
        public void toolStarted(ToolCall call) {
            events.add("tool-start:" + call.toolName());
        }

        @Override
        public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
            events.add((result.success() ? "tool-success:" : "tool-failed:") + call.toolName());
        }

        @Override
        public void finished(FinishDecision decision) {
            events.add("finish:" + decision.answer());
        }

        @Override
        public void failed(String reason) {
            events.add("failed:" + reason);
        }

        private List<String> events() {
            return events;
        }

        private List<String> toolNames(List<ToolDefinition> tools) {
            List<String> names = new ArrayList<String>();
            for (ToolDefinition tool : tools) {
                names.add(tool.name());
            }
            return names;
        }
    }

    private final class EngineFixture {
        private final ToolRegistry registry = new ToolRegistry();
        private RunLogger runLogger = new RunLoggerAdapter();
        private int maxSteps = 4;
        private boolean thinking = false;
        private PromptComposer promptComposer = null;
        private ContextCompactor contextCompactor = null;

        private EngineFixture withTools(Tool... tools) {
            for (Tool tool : tools) {
                registry.register(tool);
            }
            return this;
        }

        private EngineFixture withMaxSteps(int value) {
            maxSteps = value;
            return this;
        }

        private EngineFixture withThinking(boolean value) {
            thinking = value;
            return this;
        }

        private EngineFixture withRunLogger(RunLogger value) {
            runLogger = value;
            return this;
        }

        private EngineFixture withPromptComposer(PromptComposer value) {
            promptComposer = value;
            return this;
        }

        private EngineFixture withContextCompactor(ContextCompactor value) {
            contextCompactor = value;
            return this;
        }

        private RunResult run(ModelProvider provider, String taskId, String goal) {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AgentEngine engine = createEngine(provider, executor);
            try {
                return engine.run(new Task(taskId, goal));
            } finally {
                engine.shutdown();
            }
        }

        private RunResult run(ModelProvider provider, AgentSession session, String taskId, String goal) {
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AgentEngine engine = createEngine(provider, executor);
            try {
                return engine.run(session, new Task(taskId, goal));
            } finally {
                engine.shutdown();
            }
        }

        private AgentEngine createEngine(ModelProvider provider, ExecutorService executor) {
            PromptComposer composer = promptComposer == null ? null : promptComposer;
            ContextCompactor compactor = contextCompactor == null
                    ? new ContextCompactor()
                    : contextCompactor;
            if (composer == null) {
                return new AgentEngine(provider, registry, maxSteps, thinking, runLogger, executor, compactor);
            }
            return new AgentEngine(provider, registry, maxSteps, thinking, runLogger, executor,
                    composer, Path.of("."), compactor);
        }
    }
}
