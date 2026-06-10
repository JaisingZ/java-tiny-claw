package io.github.tinyclaw.agent.communication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 同工作区串行执行器。
 */
public final class WorkspaceSerialExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Object monitor = new Object();
    private int pendingTasks;

    public WorkspaceSerialExecutor() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 提交一个串行任务。
     */
    public void submit(Runnable task) {
        synchronized (monitor) {
            pendingTasks++;
        }
        executor.submit(() -> {
            try {
                task.run();
            } finally {
                synchronized (monitor) {
                    pendingTasks--;
                    monitor.notifyAll();
                }
            }
        });
    }

    /**
     * 等待当前队列清空。
     */
    public boolean awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        synchronized (monitor) {
            while (pendingTasks > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
            return true;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
