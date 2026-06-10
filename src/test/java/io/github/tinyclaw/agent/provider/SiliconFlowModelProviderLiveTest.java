package io.github.tinyclaw.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.runtime.RunStatus;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ParallelToolDecision;
import io.github.tinyclaw.agent.domain.Task;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.runtime.AgentEngine;
import io.github.tinyclaw.agent.runtime.RunResult;
import io.github.tinyclaw.agent.tool.Tool;
import io.github.tinyclaw.agent.tool.ToolRegistry;
import io.github.tinyclaw.agent.tool.ToolResult;
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
 * SiliconFlow 真实接口测试
 * 默认跳过 需要显式加 -Dsiliconflow.live=true
 */
@EnabledIfSystemProperty(named = "siliconflow.live", matches = "true")
class SiliconFlowModelProviderLiveTest {

    private static final String SYSTEM_PROMPT = "你是 Tiny Agent Harness 的模型 Provider。";

    @Test
    void mainLoopPrintsProviderDebugWithRealApi() {
        StringBuilder providerExchange = new StringBuilder();
        AgentEngine engine = new AgentEngine(
                new SiliconFlowModelProvider(liveConfig(),
                        line -> providerExchange.append(line).append(System.lineSeparator())),
                new ToolRegistry(),
                1,
                true);

        RunResult result = engine.run(new Task("live-thinking-trace",
                "请直接用中文回答：合肥天气测试完成。不要调用工具。"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
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

        Decision decision = provider.decide(AgentContext.create(new Task("live-thinking-hefei-weather",
                        "请先思考如何查询今天合肥天气，不要调用工具，不要给最终答案。")),
                DecisionPhase.THINKING, Collections.<ToolDefinition>emptyList(), SYSTEM_PROMPT).decision();

        assertThat(decision).isInstanceOf(ThinkingDecision.class);
        ThinkingDecision thinking = (ThinkingDecision) decision;
        assertThat(thinking.thought()).isNotBlank().containsAnyOf("合肥", "天气");
    }

    @Test
    void actionPhaseUsesRealApiForReadFileToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentContext.create(new Task("live-tool-read-file",
                        "必须调用 read_file 工具读取 a.txt。工具参数 path 必须是 a.txt，不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(readFileToolDefinition()), SYSTEM_PROMPT).decision();

        assertThat(decision).isInstanceOf(ToolDecision.class);
        ToolDecision toolDecision = (ToolDecision) decision;
        assertThat(toolDecision.call().toolName()).isEqualTo("read_file");
        assertThat(toolDecision.call().arguments()).containsKey("path");
        assertThat(String.valueOf(toolDecision.call().arguments().get("path"))).contains("a.txt");
    }

    @Test
    void actionPhaseUsesRealApiForParallelToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentContext.create(new Task("live-parallel-tools",
                        "必须并行调用两个 read_file 工具，分别读取 a.txt 和 b.txt。不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(readFileToolDefinition()), SYSTEM_PROMPT).decision();

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
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String name() { return "read_file"; }
            @Override public ToolResult execute(ToolCall call, AgentContext state) {
                try { Thread.sleep(1000); } catch (InterruptedException e) {}
                return ToolResult.success("Content of " + call.arguments().get("path"));
            }
            @Override public boolean isSideEffect() { return false; }
            @Override public ToolDefinition definition() { return readFileToolDefinition(); }
        });

        AgentEngine engine = new AgentEngine(
                new SiliconFlowModelProvider(liveConfig()),
                registry,
                5,
                true);

        RunResult result = engine.run(new Task("live-parallel-exec",
                "请同时调用两个 read_file 工具分别读取 a.txt 和 b.txt 的内容（它们是独立的）。不要使用任何脚本或其它工具。在你拿到这两个文件的内容后，直接结束任务并告诉我这两个文件的内容，例如 'a.txt 的内容是...，b.txt 的内容是...'。"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(result.observations()).anyMatch(text -> text.contains("Content of"));
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

}
