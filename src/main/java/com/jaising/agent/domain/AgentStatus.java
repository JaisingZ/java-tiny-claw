package com.jaising.agent.domain;

/**
 * Agent 运行状态
 * 只保留最小状态集
 */
public enum AgentStatus {
  RUNNING,
  PAUSED,
  SUCCESS,
  FAILED
}
