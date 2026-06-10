package io.github.tinyclaw.agent.communication;

import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.domain.ToolDecision;
import io.github.tinyclaw.agent.domain.ToolDefinition;
import io.github.tinyclaw.agent.runtime.RunLogger;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.nio.file.Path;
import java.util.List;

/**
 * 面向聊天会话的 RunLogger 基类。
 */
public abstract class AbstractChatRunLogger implements RunLogger {

    private final ChatSession session;

    protected AbstractChatRunLogger(ChatSession session) {
        this.session = session;
    }

    protected ChatSession session() {
        return session;
    }

    @Override
    public void writeLine(String line) {
        session.sendText(line);
    }

    @Override
    public void writeBlankLine() {
        session.sendText("");
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
        session.sendText(decision.answer());
    }

    @Override
    public void failed(String reason) {
        session.sendError("Agent 运行失败：" + reason);
    }
}
