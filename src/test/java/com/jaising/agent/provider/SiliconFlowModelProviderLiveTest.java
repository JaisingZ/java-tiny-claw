package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.ParallelToolDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.runtime.AgentEngine;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEvent;
import com.jaising.agent.trace.TraceEventType;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * SiliconFlow 真实接口测试
 * 默认跳过 需要显式加 -Dsiliconflow.live=true
 */
@EnabledIfSystemProperty(named = "siliconflow.live", matches = "true")
class SiliconFlowModelProviderLiveTest {

    @Test
    void mainLoopPrintsThinkingTraceWithRealApi() {
        InMemoryTraceRecorder traceRecorder = new InMemoryTraceRecorder();
        StringBuilder providerExchange = new StringBuilder();
        AgentEngine engine = new AgentEngine(
                new SiliconFlowModelProvider(liveConfig(),
                        line -> providerExchange.append(line).append(System.lineSeparator())),
                new ToolRegistry(),
                new InMemoryStateStore(),
                traceRecorder,
                1,
                true);

        RunResult result = engine.run(new Task("live-thinking-trace",
                "请直接用中文回答：合肥天气测试完成。不要调用工具。"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(thinkingResponse(traceRecorder.events()).detail()).isNotBlank();
        assertThat(providerExchange.toString())
                .contains("========== [Provider][THINKING] Request JSON ==========")
                .contains("========== [Provider][THINKING] Response JSON ==========")
                .contains("========== [Provider][THINKING] Parsed Decision ==========")
                .contains("========== [Provider][ACTION] Request JSON ==========")
                .contains("========== [Provider][ACTION] Response JSON ==========")
                .contains("========== [Provider][ACTION] Parsed Decision ==========")
                .contains("\"messages\"")
                .contains("\"choices\"")
                .doesNotContain("Bearer ");
    }

    @Test
    void thinkingPhaseUsesRealApiForHefeiWeatherTask() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-thinking-hefei-weather",
                        "请先思考如何查询今天合肥天气，不要调用工具，不要给最终答案。")),
                DecisionPhase.THINKING, Collections.<ToolDefinition>emptyList());

        assertThat(decision).isInstanceOf(ThinkingDecision.class);
        ThinkingDecision thinking = (ThinkingDecision) decision;
        assertThat(thinking.thought()).isNotBlank().containsAnyOf("合肥", "天气");
    }

    @Test
    void actionPhaseUsesRealApiForReadFileToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-tool-read-file",
                        "必须调用 read_file 工具读取 a.txt。工具参数 path 必须是 a.txt，不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(readFileToolDefinition()));

        assertThat(decision).isInstanceOf(ToolDecision.class);
        ToolDecision toolDecision = (ToolDecision) decision;
        assertThat(toolDecision.call().toolName()).isEqualTo("read_file");
        assertThat(toolDecision.call().arguments()).containsKey("path");
        assertThat(String.valueOf(toolDecision.call().arguments().get("path"))).contains("a.txt");
    }

    @Test
    void actionPhaseUsesRealApiForParallelToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-parallel-tools",
                        "必须并行调用两个 read_file 工具，分别读取 a.txt 和 b.txt。不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(readFileToolDefinition()));

        assertThat(decision).isInstanceOf(ParallelToolDecision.class);
        ParallelToolDecision parallelDecision = (ParallelToolDecision) decision;
        assertThat(parallelDecision.getCalls()).hasSize(2);

        Set<String> paths = new HashSet<>();
        for (ToolCall call : parallelDecision.getCalls()) {
            assertThat(call.toolName()).isEqualTo("read_file");
            paths.add(String.valueOf(call.arguments().get("path")));
        }
        assertThat(paths).contains("a.txt", "b.txt");
    }

    @Test
    void actionPhaseUsesRealApiForParallelToolExecution() {
        InMemoryTraceRecorder traceRecorder = new InMemoryTraceRecorder();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String name() { return "read_file"; }
            @Override public ToolResult execute(ToolCall call, AgentState state) {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                return ToolResult.success("Content of " + call.arguments().get("path"));
            }
            @Override public boolean isSideEffect() { return false; }
            @Override public ToolDefinition definition() { return readFileToolDefinition(); }
        });

        AgentEngine engine = new AgentEngine(
                new SiliconFlowModelProvider(liveConfig()),
                registry,
                new InMemoryStateStore(),
                traceRecorder,
                5,
                true);

        RunResult result = engine.run(new Task("live-parallel-exec",
                "请同时调用两个 read_file 工具分别读取 a.txt 和 b.txt 的内容（它们是独立的）。不要使用任何脚本或其它工具。在你拿到这两个文件的内容后，直接结束任务并告诉我这两个文件的内容，例如 'a.txt 的内容是...，b.txt 的内容是...'。"));

        assertThat(result.state().status()).isEqualTo(AgentStatus.SUCCESS);
        List<TraceEvent> toolCalls = traceRecorder.events().stream()
                .filter(e -> e.type() == TraceEventType.TOOL_CALL)
                .collect(Collectors.toList());

        if (toolCalls.size() >= 2) {
            String thread1 = extractThread(toolCalls.get(0).detail());
            String thread2 = extractThread(toolCalls.get(1).detail());
            assertThat(thread1).isNotBlank();
            assertThat(thread2).isNotBlank();
        }
    }

    private String extractThread(String detail) {
        int idx = detail.indexOf("thread=");
        if (idx < 0) return "unknown";
        return detail.substring(idx + 7);
    }

    private SiliconFlowConfig liveConfig() {
        SiliconFlowConfig config = SiliconFlowConfig.load(Path.of("src/main/resources/agent.properties"));
        assumeFalse(config.apiKey().isBlank(), "siliconflow.apiKey is empty");
        return config;
    }

    private ToolDefinition readFileToolDefinition() {
        Map<String, Object> path = new LinkedHashMap<String, Object>();
        path.put("type", "string");
        path.put("description", "Path relative to the workspace");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("path", path);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("path"));

        return new ToolDefinition("read_file", "Read a file", parameters);
    }

    private TraceEvent thinkingResponse(List<TraceEvent> events) {
        for (TraceEvent event : events) {
            if (event.type() == TraceEventType.THINKING_RESPONSE) {
                return event;
            }
        }
        throw new AssertionError("Missing trace event: " + TraceEventType.THINKING_RESPONSE);
    }
}
