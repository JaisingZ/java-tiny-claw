package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 控制台运行日志。
 *
 * @deprecated 使用 {@link Slf4jRunLogger}，控制台输出由 logback 统一管理。
 */
@Deprecated
public final class ConsoleRunLogger implements RunLogger {

    private final RunLogger delegate;

    /**
     * 创建默认输出详细事件的控制台日志。
     */
    public ConsoleRunLogger(PrintStream output) {
        this(output, true);
    }

    /**
     * 创建可控制事件详细程度的控制台日志。
     */
    public ConsoleRunLogger(PrintStream output, boolean verboseEvents) {
        this.delegate = new Slf4jRunLogger(verboseEvents);
    }

    /**
     * 创建标准输出运行日志。
     */
    public static ConsoleRunLogger standardOutput(boolean verboseEvents) {
        return new ConsoleRunLogger(null, verboseEvents);
    }

    @Override
    public void writeLine(String line) {
        delegate.writeLine(line);
    }

    @Override
    public void writeBlankLine() {
        delegate.writeBlankLine();
    }

    @Override
    public void registryMounted(String toolName) {
        delegate.registryMounted(toolName);
    }

    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
        delegate.engineStarted(workDir, model, maxSteps, enableThinking, tools);
    }

    @Override
    public void turnStarted(int turn) {
        delegate.turnStarted(turn);
    }

    @Override
    public void thinkingStarted() {
        delegate.thinkingStarted();
    }

    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        delegate.thinkingCompleted(decision, durationMillis);
    }

    @Override
    public void actionStarted(List<ToolDefinition> tools) {
        delegate.actionStarted(tools);
    }

    @Override
    public void toolDecision(ToolDecision decision) {
        delegate.toolDecision(decision);
    }

    @Override
    public void toolStarted(ToolCall call) {
        delegate.toolStarted(call);
    }

    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        delegate.toolCompleted(call, result, durationMillis);
    }

    @Override
    public void finished(FinishDecision decision) {
        delegate.finished(decision);
    }

    @Override
    public void failed(String reason) {
        delegate.failed(reason);
    }
}
