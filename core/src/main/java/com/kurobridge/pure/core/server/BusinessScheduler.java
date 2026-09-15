// 业务回调调度抽象：协议线程 → 业务线程的派发位（下一阶段 Paper 侧投递 Bukkit 主线程）
package com.kurobridge.pure.core.server;

/** 业务回调调度器（Bukkit API 非线程安全，主线程投递在 :paper 实现）。 */
public interface BusinessScheduler {

    void dispatch(Runnable task);
}
