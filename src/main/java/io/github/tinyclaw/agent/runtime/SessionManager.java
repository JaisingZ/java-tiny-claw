package io.github.tinyclaw.agent.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内 Session 管理器。
 */
public final class SessionManager {

    private final ConcurrentMap<String, AgentSession> sessions =
            new ConcurrentHashMap<String, AgentSession>();
    private final WorkingMemoryPolicy workingMemoryPolicy;

    public SessionManager() {
        this(new WorkingMemoryPolicy());
    }

    public SessionManager(WorkingMemoryPolicy workingMemoryPolicy) {
        if (workingMemoryPolicy == null) {
            throw new IllegalArgumentException("workingMemoryPolicy must not be null");
        }
        this.workingMemoryPolicy = workingMemoryPolicy;
    }

    public AgentSession getOrCreate(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        return sessions.computeIfAbsent(sessionId,
                id -> new AgentSession(id, workingMemoryPolicy));
    }

    public int size() {
        return sessions.size();
    }
}
