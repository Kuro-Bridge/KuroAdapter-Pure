// TimeoutScheduler 生产实现：daemon 单线程调度器（不阻止 JVM 退出，AutoCloseable 收尾）
package com.kurobridge.pure.core.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** ScheduledExecutorService 后端（线程 daemon）。 */
public final class ExecutorTimeoutScheduler implements TimeoutScheduler, AutoCloseable {

    private final ScheduledExecutorService executor;

    public ExecutorTimeoutScheduler() {
        this("kurobridge-pure-timer");
    }

    public ExecutorTimeoutScheduler(String threadName) {
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public Cancellable schedule(long delayMillis, Runnable task) {
        ScheduledFuture<?> future = executor.schedule(task, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
