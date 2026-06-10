package io.github.tinyclaw.agent.runtime;

/**
 * 上下文压缩策略。
 */
public final record ContextCompactionPolicy(
        int maxContextChars,
        int retainRecentMessages,
        int maxObservationChars,
        int headChars,
        int tailChars,
        int maskThresholdChars) {

    public static final int DEFAULT_MAX_CONTEXT_CHARS = 12_000;
    public static final int DEFAULT_RETAIN_RECENT_MESSAGES = 6;
    public static final int DEFAULT_MAX_OBSERVATION_CHARS = 1_000;
    public static final int DEFAULT_HEAD_CHARS = 500;
    public static final int DEFAULT_TAIL_CHARS = 500;
    public static final int DEFAULT_MASK_THRESHOLD_CHARS = 200;

    /**
     * 使用默认策略参数创建。
     */
    public ContextCompactionPolicy() {
        this(
                DEFAULT_MAX_CONTEXT_CHARS,
                DEFAULT_RETAIN_RECENT_MESSAGES,
                DEFAULT_MAX_OBSERVATION_CHARS,
                DEFAULT_HEAD_CHARS,
                DEFAULT_TAIL_CHARS,
                DEFAULT_MASK_THRESHOLD_CHARS);
    }

    /**
     * 创建上下文压缩策略。
     */
    public ContextCompactionPolicy {
        if (maxContextChars <= 0) {
            throw new IllegalArgumentException("maxContextChars must be positive");
        }
        if (retainRecentMessages <= 0) {
            throw new IllegalArgumentException("retainRecentMessages must be positive");
        }
        if (maxObservationChars <= 0) {
            throw new IllegalArgumentException("maxObservationChars must be positive");
        }
        if (headChars <= 0) {
            throw new IllegalArgumentException("headChars must be positive");
        }
        if (tailChars <= 0) {
            throw new IllegalArgumentException("tailChars must be positive");
        }
        if (maskThresholdChars <= 0) {
            throw new IllegalArgumentException("maskThresholdChars must be positive");
        }
        if (headChars + tailChars > maxObservationChars) {
            throw new IllegalArgumentException("headChars + tailChars must not exceed maxObservationChars");
        }
    }

    /**
     * 获取上下文字符上限（兼容 get 风格）。
     */
    public int getMaxContextChars() {
        return maxContextChars();
    }

    /**
     * 获取近期消息保留条数（兼容 get 风格）。
     */
    public int getRetainRecentMessages() {
        return retainRecentMessages();
    }

    /**
     * 获取单条 observation 文本最大长度（兼容 get 风格）。
     */
    public int getMaxObservationChars() {
        return maxObservationChars();
    }

    /**
     * 获取 observation 首部保留长度（兼容 get 风格）。
     */
    public int getHeadChars() {
        return headChars();
    }

    /**
     * 获取 observation 尾部保留长度（兼容 get 风格）。
     */
    public int getTailChars() {
        return tailChars();
    }

    /**
     * 获取远期 observation 压缩阈值（兼容 get 风格）。
     */
    public int getMaskThresholdChars() {
        return maskThresholdChars();
    }
}
