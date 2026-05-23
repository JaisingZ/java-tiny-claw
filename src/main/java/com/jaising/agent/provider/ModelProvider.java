package com.jaising.agent.provider;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.ToolDefinition;
import java.util.List;

/**
 * 模型提供方
 * 只负责根据状态给出下一步决策
 */
public interface ModelProvider {
    /**
     * 根据状态和阶段返回模型决策。
     */
    Decision decide(AgentState state, DecisionPhase phase, List<ToolDefinition> availableTools);
}
