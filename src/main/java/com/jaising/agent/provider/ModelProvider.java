package com.jaising.agent.provider;

import com.jaising.agent.domain.AgentState;
import com.jaising.agent.domain.Decision;

/**
 * 模型提供方
 * 只负责根据状态给出下一步决策
 */
public interface ModelProvider {
    Decision decide(AgentState state);
}
