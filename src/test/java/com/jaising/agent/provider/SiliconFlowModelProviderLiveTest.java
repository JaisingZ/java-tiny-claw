package com.jaising.agent.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.Task;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
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
    void actionPhaseUsesRealApiForHefeiWeatherToolDecision() {
        SiliconFlowModelProvider provider = new SiliconFlowModelProvider(liveConfig());

        Decision decision = provider.decide(AgentState.create(new Task("live-tool-hefei-weather",
                        "必须调用 get_weather 工具查询今天合肥天气。工具参数 city 必须是 合肥，不要直接回答。")),
                DecisionPhase.ACTION, Collections.singletonList(weatherToolDefinition()));

        assertThat(decision).isInstanceOf(ToolDecision.class);
        ToolDecision toolDecision = (ToolDecision) decision;
        assertThat(toolDecision.call().toolName()).isEqualTo("get_weather");
        assertThat(toolDecision.call().arguments()).containsKey("city");
        assertThat(String.valueOf(toolDecision.call().arguments().get("city"))).contains("合肥");
    }

    private SiliconFlowConfig liveConfig() {
        SiliconFlowConfig config = SiliconFlowConfig.load(Path.of("src/main/resources/agent.properties"));
        assumeFalse(config.apiKey().isBlank(), "siliconflow.apiKey is empty");
        return config;
    }

    private ToolDefinition weatherToolDefinition() {
        Map<String, Object> city = new LinkedHashMap<String, Object>();
        city.put("type", "string");
        city.put("description", "城市名称，必须填写合肥");

        Map<String, Object> date = new LinkedHashMap<String, Object>();
        date.put("type", "string");
        date.put("description", "查询日期，必须填写今天");

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("city", city);
        properties.put("date", date);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("city", "date"));

        return new ToolDefinition("get_weather", "查询指定城市指定日期的天气", parameters);
    }
}
