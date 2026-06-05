package io.github.tinyclaw.agent.provider;

import io.github.tinyclaw.agent.domain.AgentContext;
import io.github.tinyclaw.agent.domain.Decision;
import io.github.tinyclaw.agent.domain.DecisionPhase;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import java.util.List;

/**
 * 模型提供方。
 * 只负责根据当前上下文和阶段给出下一步决策。
 */
public interface ModelProvider {
    /**
     * 根据当前上下文、决策阶段、可用工具和系统提示词返回模型决策。
     */
    Decision decide(AgentContext context, DecisionPhase phase, List<ToolDefinition> availableTools,
            String systemPrompt);
}
