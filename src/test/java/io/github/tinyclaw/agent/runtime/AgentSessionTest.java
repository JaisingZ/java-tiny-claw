package io.github.tinyclaw.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.github.tinyclaw.agent.domain.SessionMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 会话模型与工作内存策略测试。
 */
class AgentSessionTest {

    /**
     * 会话应记录 sessionId、创建时间和更新时间，追加记录后更新时间应变化。
     */
    @Test
    void keepsMetadataAndUpdatesTimestamp() throws Exception {
        AgentSession session = new AgentSession("chat-meta");
        assertThat(session.sessionId()).isEqualTo("chat-meta");
        assertThat(session.getSessionId()).isEqualTo("chat-meta");
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.updatedAt()).isNotNull();
        assertThat(session.updatedAt()).isBeforeOrEqualTo(session.createdAt().plusMillis(1));

        Instant beforeUpdate = session.updatedAt();
        TimeUnit.MILLISECONDS.sleep(1);
        session.append(SessionMessage.user("hello"));

        assertThat(session.updatedAt()).isAfterOrEqualTo(beforeUpdate);
        assertThat(session.workingMemory()).containsExactly(SessionMessage.user("hello"));
    }

    /**
     * 工作内存必须从末尾截取，并同时满足条数和字符上限。
     */
    @Test
    void workingMemoryRespectsMessageAndCharBudgetFromTail() {
        WorkingMemoryPolicy policy = new WorkingMemoryPolicy(3, 10);
        AgentSession session = new AgentSession("chat-budget", policy);
        session.append(SessionMessage.user("hello"));
        session.append(SessionMessage.assistant("world"));
        session.append(SessionMessage.user("x"));
        session.append(SessionMessage.observation("o"));

        assertThat(session.workingMemory())
                .containsExactly(
                        SessionMessage.assistant("world"),
                        SessionMessage.user("x"),
                        SessionMessage.observation("o"));
    }

    /**
     * 字符预算不足时继续丢弃旧消息。
     */
    @Test
    void workingMemoryDropsOlderMessagesWhenCharBudgetWouldOverflow() {
        WorkingMemoryPolicy policy = new WorkingMemoryPolicy(10, 3);
        AgentSession session = new AgentSession("chat-char-budget", policy);
        session.append(SessionMessage.user("old"));
        session.append(SessionMessage.assistant("too-long"));
        session.append(SessionMessage.user("new"));

        assertThat(session.workingMemory()).containsExactly(SessionMessage.user("new"));
    }

    /**
     * 截断窗口开头不能为孤立 Observation，必要时继续向后丢弃。
     */
    @Test
    void trimsLeadingObservationWhenItBecomesWindowHead() {
        WorkingMemoryPolicy policy = new WorkingMemoryPolicy(3, 100);
        AgentSession session = new AgentSession("chat-observation", policy);
        session.append(SessionMessage.user("question"));
        session.append(SessionMessage.observation("prev-observation"));
        session.append(SessionMessage.assistant("answer"));
        session.append(SessionMessage.observation("tail-observation"));

        assertThat(session.workingMemory())
                .containsExactly(
                        SessionMessage.assistant("answer"),
                        SessionMessage.observation("tail-observation"));
    }

    /**
     * 返回工作内存副本且不可变，外部修改不得影响内部历史。
     */
    @Test
    void workingMemorySnapshotIsIndependentAndUnmodifiable() {
        AgentSession session = new AgentSession("chat-immutable", new WorkingMemoryPolicy(20, 200));
        session.append(SessionMessage.user("hello"));

        List<SessionMessage> snapshot = session.workingMemory();
        assertThatThrownBy(() -> snapshot.add(SessionMessage.assistant("nope")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> session.history().add(SessionMessage.assistant("nope")))
                .isInstanceOf(UnsupportedOperationException.class);

        session.append(SessionMessage.assistant("world"));
        assertThat(session.history())
                .containsExactly(
                        SessionMessage.user("hello"),
                        SessionMessage.assistant("world"));
        assertThat(session.workingMemory())
                .containsExactly(
                        SessionMessage.user("hello"),
                        SessionMessage.assistant("world"));
    }

    /**
     * 并发写入历史时不会出现丢失/越界，且返回集合不会被外部污染。
     */
    @Test
    void concurrentAppendsKeepHistoryThreadSafe() throws Exception {
        WorkingMemoryPolicy policy = new WorkingMemoryPolicy(200, 8_000);
        AgentSession session = new AgentSession("chat-thread-safe", policy);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        int perThread = 80;
        int threadCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();
        for (int thread = 0; thread < threadCount; thread++) {
            final int index = thread;
            tasks.add(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) {
                    session.append(SessionMessage.user("u-" + index + "-" + i));
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<Future<Void>>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            assertDoesNotThrow(() -> future.get(2, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(session.history()).hasSize(threadCount * perThread);
        assertThat(session.workingMemory()).hasSize(policy.maxMessages());
    }

    /**
     * SessionManager 应按 sessionId 隔离并复用实例。
     */
    @Test
    void sessionManagerGetsOrCreateSameIdAndIsolatedForDifferentIds() {
        SessionManager manager = new SessionManager();
        AgentSession first = manager.getOrCreate("a");
        AgentSession second = manager.getOrCreate("a");
        AgentSession other = manager.getOrCreate("b");

        assertThat(first).isSameAs(second);
        assertThat(other).isNotSameAs(first);
        assertThat(other.sessionId()).isEqualTo("b");
    }

    /**
     * SessionManager 创建的新 Session 应使用传入的工作内存策略。
     */
    @Test
    void sessionManagerUsesConfiguredWorkingMemoryPolicy() {
        SessionManager manager = new SessionManager(new WorkingMemoryPolicy(1, 100));
        AgentSession session = manager.getOrCreate("a");

        session.append(SessionMessage.user("old"), SessionMessage.assistant("new"));

        assertThat(session.workingMemory()).containsExactly(SessionMessage.assistant("new"));
    }

    /**
     * 自定义工作内存策略参数必须是正整数。
     */
    @Test
    void workingMemoryPolicyDefaultsAndValidatesInput() {
        WorkingMemoryPolicy defaultPolicy = new WorkingMemoryPolicy();
        assertThat(defaultPolicy.maxMessages()).isEqualTo(12);
        assertThat(defaultPolicy.maxChars()).isEqualTo(12_000);

        assertThatThrownBy(() -> new WorkingMemoryPolicy(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkingMemoryPolicy(10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
