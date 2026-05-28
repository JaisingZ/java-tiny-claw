package com.jaising.agent.runtime;

import com.jaising.agent.domain.FinishDecision;
import com.jaising.agent.domain.ThinkingDecision;
import com.jaising.agent.domain.ToolCall;
import com.jaising.agent.domain.ToolDecision;
import com.jaising.agent.domain.ToolDefinition;
import com.jaising.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 运行日志接口
 * 用于输出面向人的主循环进展
 */
public interface RunLogger {

    /**
     * 记录工具挂载。
     */
    void registryMounted(String toolName);

    /**
     * 记录引擎启动。
     */
    void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools);

    /**
     * 记录回合开始。
     */
    void turnStarted(int turn);

    /**
     * 记录慢思考开始。
     */
    void thinkingStarted();

    /**
     * 记录慢思考完成。
     */
    void thinkingCompleted(ThinkingDecision decision, long durationMillis);

    /**
     * 记录行动阶段开始。
     */
    void actionStarted(List<ToolDefinition> tools);

    /**
     * 记录模型请求工具调用。
     */
    void toolDecision(ToolDecision decision);

    /**
     * 记录工具执行开始。
     */
    void toolStarted(ToolCall call);

    /**
     * 记录工具执行完成。
     */
    void toolCompleted(ToolCall call, ToolResult result, long durationMillis);

    /**
     * 记录最终回复。
     */
    void finished(FinishDecision decision);

    /**
     * 记录失败。
     */
    void failed(String reason);
}
