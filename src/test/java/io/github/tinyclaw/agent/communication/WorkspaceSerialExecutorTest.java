package io.github.tinyclaw.agent.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 工作区串行执行器测试。
 */
class WorkspaceSerialExecutorTest {

    /**
     * 多个任务提交后同一时刻只允许一个任务执行。
     */
    @Test
    void runsSubmittedTasksOneAtATime() throws Exception {
        WorkspaceSerialExecutor executor = new WorkspaceSerialExecutor();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(3);
        List<Integer> order = new ArrayList<Integer>();

        for (int i = 0; i < 3; i++) {
            final int value = i;
            executor.submit(() -> {
                int current = active.incrementAndGet();
                maxActive.updateAndGet(previous -> Math.max(previous, current));
                order.add(value);
                started.countDown();
                sleep(30);
                active.decrementAndGet();
            });
        }

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(executor.awaitIdle(2, TimeUnit.SECONDS)).isTrue();
        executor.close();
        assertThat(maxActive.get()).isEqualTo(1);
        assertThat(order).containsExactly(0, 1, 2);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
