package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.SessionMessage;
import io.github.tinyclaw.agent.domain.SessionMessageKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 工作内存窗口策略。
 *
 * @param maxMessages 允许保留的最大消息数。
 * @param maxChars 允许保留的最大字符总数。
 */
public final class WorkingMemoryPolicy {

    public static final int DEFAULT_MAX_MESSAGES = 12;
    public static final int DEFAULT_MAX_CHARS = 12_000;

    private final int maxMessages;
    private final int maxChars;

    /**
     * 使用默认策略（12 条 / 12000 字符）初始化。
     */
    public WorkingMemoryPolicy() {
        this(DEFAULT_MAX_MESSAGES, DEFAULT_MAX_CHARS);
    }

    /**
     * 使用自定义策略初始化。
     *
     * @param maxMessages 正整数，最大消息数。
     * @param maxChars 正整数，最大字符数。
     */
    public WorkingMemoryPolicy(int maxMessages, int maxChars) {
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be a positive integer");
        }
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be a positive integer");
        }
        this.maxMessages = maxMessages;
        this.maxChars = maxChars;
    }

    /**
     * 计算当前策略下应返回的工作内存窗口。
     */
    public List<SessionMessage> apply(List<SessionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        int charCount = 0;
        int messageCount = 0;
        int start = messages.size();
        while (start > 0) {
            SessionMessage candidate = messages.get(start - 1);
            int candidateChars = candidate.content() == null ? 0 : candidate.content().length();
            if (messageCount + 1 > maxMessages) {
                break;
            }
            if (charCount + candidateChars > maxChars) {
                break;
            }
            start--;
            messageCount++;
            charCount += candidateChars;
        }

        List<SessionMessage> window = new ArrayList<SessionMessage>(messages.subList(start, messages.size()));
        while (!window.isEmpty() && window.get(0).kind() == SessionMessageKind.OBSERVATION) {
            window.remove(0);
        }
        return Collections.unmodifiableList(window);
    }

    /**
     * 获取当前最大消息数。
     */
    public int maxMessages() {
        return maxMessages;
    }

    /**
     * 获取当前最大字符数。
     */
    public int maxChars() {
        return maxChars;
    }

    /**
     * 获取当前最大消息数（兼容 get 风格）。
     */
    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * 获取当前最大字符数（兼容 get 风格）。
     */
    public int getMaxChars() {
        return maxChars;
    }
}
