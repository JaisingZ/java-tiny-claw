package com.jaising.agent.provider;

import com.jaising.agent.domain.AgentContext;
import com.jaising.agent.domain.Decision;
import com.jaising.agent.domain.DecisionPhase;
import com.jaising.agent.domain.ToolDefinition;
import java.util.List;

/**
 * 模型提供方。
 * 只负责根据当前上下文和阶段给出下一步决策。
 */
public interface ModelProvider {
    /**
     * 根据当前上下文、决策阶段和可用工具返回模型决策。
     */
    Decision decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools);
}
