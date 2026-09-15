// 空闲检测：任意入帧（含校验失败帧）重置计时；阈值内无入帧触发回调；0 = 禁用
package com.kurobridge.pure.core.server;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 空闲计时器（重挂式：每次入帧 cancel + re-schedule，等效主仓 armIdleTimeout 的取消重挂法）。
 * 回调触发（close 1001 "idle timeout"）由持有方决定；线程安全（WS 线程与定时器线程共触）。
 */
public final class IdleTracker {

    private final long timeoutMs;
    private final TimeoutScheduler scheduler;
    private final Runnable onTimeout;
    private final AtomicReference<TimeoutScheduler.Cancellable> timer = new AtomicReference<>();

    public IdleTracker(long timeoutMs, TimeoutScheduler scheduler, Runnable onTimeout) {
        this.timeoutMs = timeoutMs;
        this.scheduler = scheduler;
        this.onTimeout = onTimeout;
    }

    /** 任何入帧调用：重置计时（0 = 禁用，空操作）。 */
    public void onActivity() {
        cancel();
        if (timeoutMs > 0) {
            timer.set(scheduler.schedule(timeoutMs, onTimeout));
        }
    }

    /** 停止计时（连接关闭/握手被拒时）。 */
    public void cancel() {
        TimeoutScheduler.Cancellable pending = timer.getAndSet(null);
        if (pending != null) {
            pending.cancel();
        }
    }
}
