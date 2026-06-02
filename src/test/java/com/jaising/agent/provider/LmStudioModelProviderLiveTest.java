package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.runtime.RunStatus;
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
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * LM Studio 真实接口测试
 * 默认跳过 需要显式加 -Dlmstudio.live=true
 */
@EnabledIfSystemProperty(named = "lmstudio.live", matches = "true")
class LmStudioModelProviderLiveTest {

    @Test
    void mainLoopPrintsProviderDebugWithRealApi() {
        StringBuilder providerExchange = new StringBuilder();
        AgentEngine engine = new AgentEngine(
                new LmStudioModelProvider(liveConfig(),
                        line -> providerExchange.append(line).append(System.lineSeparator())),
                new ToolRegistry(),
                1,
                true);

        RunResult result = engine.run(new Task("live-thinking-trace",
                "请直接用中文回答：LM Studio 启动测试完成。不要调用工具。"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(providerExchange.toString())
                .contains("========== [Provider][THINKING] Request JSON ==========")
                .contains("========== [Provider][THINKING] Response JSON ==========")
                .contains("========== [Provider][THINKING] Parsed Decision ==========")
                .contains("========== [Provider][ACTION] Request JSON ==========")
                .contains("========== [Provider][ACTION] Response JSON ==========")
                .contains("========== [Provider][ACTION] Parsed Decision ==========")
                .contains("\"messages\"")
                .contains("\"choices\"");
    }

    @Test
    void thinkingPhaseUsesRealApiForWeatherTask() {
        LmStudioModelProvider provider = new LmStudioModelProvider(liveConfig());

        Decision decision = provider.decide(AgentContext.create(new Task("live-thinking-weather",
                        "请先思考如何查询今天合肥天气，不要调用工具，不要给最终答案。")),
                DecisionPhase.THINKING, Collections.<ToolDefinition>emptyList());

        assertThat(decision).isInstanceOf(ThinkingDecision.class);
        ThinkingDecision thinking = (ThinkingDecision) decision;
        assertThat(thinking.thought()).isNotBlank().containsAnyOf("合肥", "天气");
    }

    @Test
    void actionPhaseUsesRealApiForReadFileToolDecision() {
        LmStudioModelProvider provider = new LmStudioModelProvider(liveConfig());

        Decision decision = provider.decide(AgentContext.create(new Task("live-tool-read-file",
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
        LmStudioModelProvider provider = new LmStudioModelProvider(liveConfig());

        Decision decision = provider.decide(AgentContext.create(new Task("live-parallel-tools",
                        "必须并行调用两个 read_file 工具，分别读取 a.txt 和 b.txt。不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(readFileToolDefinition()));

        assertThat(decision).isInstanceOf(ParallelToolDecision.class);
        ParallelToolDecision parallelDecision = (ParallelToolDecision) decision;
        assertThat(parallelDecision.getCalls()).hasSize(2);

        Set<String> paths = new HashSet<String>();
        for (ToolCall call : parallelDecision.getCalls()) {
            assertThat(call.toolName()).isEqualTo("read_file");
            paths.add(String.valueOf(call.arguments().get("path")));
        }
        assertThat(paths).contains("a.txt", "b.txt");
    }

    @Test
    void actionPhaseUsesRealApiForParallelToolExecution() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String name() { return "read_file"; }
            @Override public ToolResult execute(ToolCall call, AgentContext state) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ToolResult.success("Content of " + call.arguments().get("path"));
            }
            @Override public boolean isSideEffect() { return false; }
            @Override public ToolDefinition definition() { return readFileToolDefinition(); }
        });

        AgentEngine engine = new AgentEngine(
                new LmStudioModelProvider(liveConfig()),
                registry,
                5,
                true);

        RunResult result = engine.run(new Task("live-parallel-exec",
                "请同时调用两个 read_file 工具分别读取 a.txt 和 b.txt 的内容（它们是独立的）。不要使用任何脚本或其它工具。在你拿到这两个文件的内容后，直接结束任务并告诉我这两个文件的内容，例如 'a.txt 的内容是...，b.txt 的内容是...'。"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).anyMatch(text -> text.contains("Content of"));
    }

    private LmStudioConfig liveConfig() {
        return LmStudioConfig.load(Path.of("src/main/resources/agent.properties"));
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

}
