package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 静默运行日志
 * 保持默认运行无额外输出
 */
public final class NoopRunLogger implements RunLogger {

    /**
     * 共享静默日志实例。
     */
    public static final NoopRunLogger INSTANCE = new NoopRunLogger();

    private NoopRunLogger() {
    }

    /**
     * 忽略文本输出。
     */
    @Override
    public void writeLine(String line) {
    }

    /**
     * 忽略空行输出。
     */
    @Override
    public void writeBlankLine() {
    }

    /**
     * 忽略工具挂载事件。
     */
    @Override
    public void registryMounted(String toolName) {
    }

    /**
     * 忽略引擎启动事件。
     */
    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
    }

    /**
     * 忽略 turn 开始事件。
     */
    @Override
    public void turnStarted(int turn) {
    }

    /**
     * 忽略 thinking 开始事件。
     */
    @Override
    public void thinkingStarted() {
    }

    /**
     * 忽略 thinking 完成事件。
     */
    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
    }

    /**
     * 忽略 action 开始事件。
     */
    @Override
    public void actionStarted(List<ToolDefinition> tools) {
    }

    /**
     * 忽略工具决策事件。
     */
    @Override
    public void toolDecision(ToolDecision decision) {
    }

    /**
     * 忽略工具开始事件。
     */
    @Override
    public void toolStarted(ToolCall call) {
    }

    /**
     * 忽略工具完成事件。
     */
    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
    }

    /**
     * 忽略完成事件。
     */
    @Override
    public void finished(FinishDecision decision) {
    }

    /**
     * 忽略失败事件。
     */
    @Override
    public void failed(String reason) {
    }
}
