package io.github.tinyclaw.agent.communication.telegram;

import io.github.tinyclaw.agent.communication.AbstractChatRunLogger;
import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;

/**
 * Telegram 消息运行日志。
 */
public final class TelegramRunLogger extends AbstractChatRunLogger {

    public TelegramRunLogger(ChatSession session) {
        super(session);
    }

    @Override
    public void thinkingStarted() {
        session().sendStatus("模型正在慢思考...");
    }

    @Override
    public void thinkingCompleted(ThinkingDecision decision, long durationMillis) {
        session().sendStatus("慢思考完成，耗时 " + durationMillis + "ms。");
    }

    @Override
    public void toolStarted(ToolCall call) {
        session().sendStatus("准备执行工具 " + call.toolName() + "，参数 " + call.arguments());
    }

    @Override
    public void toolCompleted(ToolCall call, ToolResult result, long durationMillis) {
        if (result.success()) {
            session().sendStatus("工具 " + call.toolName() + " 执行成功，耗时 " + durationMillis + "ms。");
            return;
        }
        session().sendError("工具 " + call.toolName() + " 执行失败：" + result.errorMessage());
    }
}
