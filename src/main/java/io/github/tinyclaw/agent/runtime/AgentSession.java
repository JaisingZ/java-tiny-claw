package io.github.tinyclaw.agent.runtime;

import io.github.tinyclaw.agent.domain.SessionMessage;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * 一个持续会话的进程内历史池。
 */
public final class AgentSession {

    private final String sessionId;
    private final Instant createdAt;
    private volatile Instant updatedAt;
    private final WorkingMemoryPolicy workingMemoryPolicy;
    private final Deque<SessionMessage> history;
    private final Object historyLock;
    private final Object metricsLock;
    private SessionMetrics metrics;

    /**
     * 创建默认策略会话。
     */
    public AgentSession(String sessionId) {
        this(sessionId, new WorkingMemoryPolicy());
    }

    /**
     * 使用自定义策略创建会话。
     */
    public AgentSession(String sessionId, WorkingMemoryPolicy workingMemoryPolicy) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.workingMemoryPolicy = Objects.requireNonNull(workingMemoryPolicy, "workingMemoryPolicy");
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.history = new ArrayDeque<SessionMessage>();
        this.historyLock = new Object();
        this.metricsLock = new Object();
        this.metrics = SessionMetrics.empty();
    }

    /**
     * 追加一条会话消息。
     */
    public void append(SessionMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
        synchronized (historyLock) {
            history.addLast(message);
            updatedAt = Instant.now();
        }
    }

    /**
     * 批量追加会话消息。
     */
    public void append(SessionMessage... messages) {
        if (messages == null || messages.length == 0) {
            return;
        }
        synchronized (historyLock) {
            for (SessionMessage message : messages) {
                if (message == null) {
                    throw new IllegalArgumentException("message");
                }
                history.addLast(message);
            }
            updatedAt = Instant.now();
        }
    }

    /**
     * 获取完整历史快照。
     */
    public List<SessionMessage> history() {
        synchronized (historyLock) {
            return Collections.unmodifiableList(new ArrayList<SessionMessage>(history));
        }
    }

    /**
     * 获取该会话当前工作内存。
     */
    public List<SessionMessage> workingMemory() {
        return workingMemory(workingMemoryPolicy);
    }

    /**
     * 使用指定策略获取工作内存。
     */
    public List<SessionMessage> workingMemory(WorkingMemoryPolicy policy) {
        synchronized (historyLock) {
            WorkingMemoryPolicy safePolicy = policy == null ? workingMemoryPolicy : policy;
            return safePolicy.apply(new ArrayList<SessionMessage>(history));
        }
    }

    /**
     * 累加一次运行的指标到当前会话。
     */
    public void record(RunMetrics runMetrics) {
        synchronized (metricsLock) {
            metrics = metrics.plus(runMetrics);
            updatedAt = Instant.now();
        }
    }

    /**
     * 返回当前会话累计指标快照。
     */
    public SessionMetrics metrics() {
        synchronized (metricsLock) {
            return metrics;
        }
    }

    public SessionMetrics getMetrics() {
        return metrics();
    }

    /**
     * 获取会话 id。
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * 获取会话 id（兼容 get 风格）。
     */
    public String getSessionId() {
        return sessionId();
    }

    /**
     * 获取会话创建时间。
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 获取会话创建时间（兼容 get 风格）。
     */
    public Instant getCreatedAt() {
        return createdAt();
    }

    /**
     * 获取最近一次更新时间。
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 获取最近一次更新时间（兼容 get 风格）。
     */
    public Instant getUpdatedAt() {
        return updatedAt();
    }
}
