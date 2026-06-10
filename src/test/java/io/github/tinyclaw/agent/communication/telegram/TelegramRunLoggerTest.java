package io.github.tinyclaw.agent.communication.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tinyclaw.agent.communication.ChatSession;
import io.github.tinyclaw.agent.domain.FinishDecision;
import io.github.tinyclaw.agent.domain.ThinkingDecision;
import io.github.tinyclaw.agent.domain.ToolCall;
import io.github.tinyclaw.agent.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Telegram 运行日志测试。
 */
class TelegramRunLoggerTest {

    /**
     * 主循环关键事件会映射为 Telegram 可读消息。
     */
    @Test
    void sendsReadableTelegramMessagesForRunEvents() {
        RecordingSession session = new RecordingSession();
        TelegramRunLogger logger = new TelegramRunLogger(session);

        logger.thinkingStarted();
        logger.thinkingCompleted(new ThinkingDecision("inspect files"), 12L);
        logger.toolStarted(new ToolCall("read_file", Collections.<String, Object>singletonMap("path", "README.md")));
        logger.toolCompleted(new ToolCall("read_file", Collections.<String, Object>emptyMap()),
                ToolResult.success("long output"), 8L);
        logger.finished(new FinishDecision("done"));
        logger.failed("boom");

        assertThat(session.statuses()).containsExactly(
                "模型正在慢思考...",
                "慢思考完成，耗时 12ms。",
                "准备执行工具 read_file，参数 {path=README.md}",
                "工具 read_file 执行成功，耗时 8ms。");
        assertThat(session.messages()).containsExactly("done");
        assertThat(session.errors()).containsExactly("Agent 运行失败：boom");
    }

    private static final class RecordingSession implements ChatSession {
        private final List<String> messages = new ArrayList<String>();
        private final List<String> statuses = new ArrayList<String>();
        private final List<String> errors = new ArrayList<String>();

        @Override
        public void sendText(String text) {
            messages.add(text);
        }

        @Override
        public void sendStatus(String text) {
            statuses.add(text);
        }

        @Override
        public void sendError(String text) {
            errors.add(text);
        }

        private List<String> messages() {
            return messages;
        }

        private List<String> statuses() {
            return statuses;
        }

        private List<String> errors() {
            return errors;
        }
    }
}
