package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.AgentStatus;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.middleware.AllowAllMiddleware;
import com.jaising.agent.runtime.AgentEngine;
import com.jaising.agent.runtime.RunResult;
import com.jaising.agent.state.InMemoryStateStore;
import com.jaising.agent.tool.Tool;
import com.jaising.agent.tool.ToolRegistry;
import com.jaising.agent.tool.ToolResult;
import com.jaising.agent.trace.InMemoryTraceRecorder;
import com.jaising.agent.trace.TraceEventType;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * SiliconFlow 真实接口测试
 * 默认跳过 需要显式加 -Dsiliconflow.live=true
 */
@EnabledIfSystemProperty(named = "siliconflow.live", matches = "true")
class SiliconFlowModelProviderLiveTest {

    @Test
    void callsRealApiForTextCompletion() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-text",
                        "请只回复 OK，用于 java-tiny-claw 连通性验证。")),
                DecisionPhase.ACTION, Collections.<ToolDefinition>emptyList());

        assertThat(decision).isInstanceOf(FinishDecision.class);
        FinishDecision finish = (FinishDecision) decision;
        assertThat(finish.answer()).isNotBlank();
    }

    @Test
    void callsRealApiForToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-tool",
                        "必须调用 get_weather 工具查询合肥天气，不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(weatherToolDefinition()));

        assertThat(decision).isInstanceOf(ToolDecision.class);
        ToolDecision toolDecision = (ToolDecision) decision;
        assertThat(toolDecision.call().toolName()).isEqualTo("get_weather");
        assertThat(toolDecision.call().arguments()).containsKey("city");
    }

    @Test
    void runsMainLoopWithRealApiForTodayHefeiWeather() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());
        ToolRegistry registry = new ToolRegistry().register(new HefeiWeatherTool());
        InMemoryStateStore stateStore = new InMemoryStateStore();
        InMemoryTraceRecorder traceRecorder = new InMemoryTraceRecorder();
        AgentEngine engine = new AgentEngine(provider, registry,
                List.of(new AllowAllMiddleware()), stateStore, traceRecorder, 4, false);

        RunResult result = engine.run(new Task("live-main-loop-hefei-weather",
                "必须先调用 get_weather 工具查询今天合肥天气，工具参数 city 必须是 合肥。"
                        + "拿到工具观测后，用一句中文回答，答案必须包含 合肥 和 适合跑步。"));

        AgentState state = result.state();
        assertThat(state.status()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(state.observations()).singleElement()
                .satisfies(observation -> assertThat(observation)
                        .contains("合肥")
                        .contains("今天"));
        assertThat(state.finalAnswer()).contains("合肥").contains("适合跑步");
        assertThat(traceRecorder.events()).extracting(event -> event.type()).contains(
                TraceEventType.MODEL_REQUEST,
                TraceEventType.TOOL_CALL,
                TraceEventType.TOOL_RESULT,
                TraceEventType.FINISHED);
    }

    private SiliconFlowConfig liveConfig() {
        SiliconFlowConfig config = SiliconFlowConfig.load(Path.of("src/main/resources/agent.properties"));
        assumeFalse(config.apiKey().isBlank(), "siliconflow.apiKey is empty");
        return config;
    }

    private ToolDefinition weatherToolDefinition() {
        Map<String, Object> city = new LinkedHashMap<String, Object>();
        city.put("type", "string");
        city.put("description", "城市名称，例如合肥");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("city", city);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("city"));

        return new ToolDefinition("get_weather", "查询指定城市的天气", parameters);
    }

    private static final class HefeiWeatherTool implements Tool {

        @Override
        public String name() {
            return "get_weather";
        }

        @Override
        public ToolDefinition definition() {
            Map<String, Object> city = new LinkedHashMap<String, Object>();
            city.put("type", "string");
            city.put("description", "城市名称，必须填写合肥");

            Map<String, Object> properties = new LinkedHashMap<String, Object>();
            properties.put("city", city);

            Map<String, Object> parameters = new LinkedHashMap<String, Object>();
            parameters.put("type", "object");
            parameters.put("properties", properties);
            parameters.put("required", List.of("city"));

            return new ToolDefinition(name(), "查询今天指定城市的天气", parameters);
        }

        @Override
        public ToolResult execute(ToolCall call, AgentState state) {
            return ToolResult.success("今天合肥天气：晴，25度，空气质量良好，适合跑步。");
        }
    }
}
