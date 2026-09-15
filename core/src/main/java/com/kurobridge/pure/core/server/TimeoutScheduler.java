// 一次性定时器抽象（注入：hello 超时/空闲检测共用；等效主仓 clock.ts 的 TimerScheduler）
package com.kurobridge.pure.core.server;

/** 定时器注入接口：schedule 返回可取消句柄（幂等 cancel）。 */
public interface TimeoutScheduler {

    Cancellable schedule(long delayMillis, Runnable task);

    /** 取消句柄（幂等）。 */
    interface Cancellable {

        void cancel();
    }
}
