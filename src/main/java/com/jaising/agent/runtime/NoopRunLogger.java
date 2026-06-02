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
 * 静默运行日志
 * 保持默认运行无额外输出
 */
public final class NoopRunLogger implements RunLogger {

    public static final NoopRunLogger INSTANCE = new NoopRunLogger();

    private NoopRunLogger() {
    }

    @Override
    public void writeLine(String line) {
    }

    @Override
    public void writeBlankLine() {
    }

    @Override
    public void registryMounted(String toolName) {
    }

    @Override
    public void engineStarted(Path workDir, String model, int maxSteps, boolean enableThinking,
            List<ToolDefinition> tools) {
    }

    @Override
    public void turnStarted(int turn) {
    }

    @Override
    public void thinkingStarted() {
    }

    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
    }

    @Override
    public void actionStarted(List<ToolDefinition> tools) {
    }

    @Override
    public void toolDecision(ToolDecision decision) {
    }

    @Override
    public void toolStarted(ToolCall call) {
    }

    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
    }

    @Override
    public void finished(FinishDecision decision) {
    }

    @Override
    public void failed(String reason) {
    }
}
