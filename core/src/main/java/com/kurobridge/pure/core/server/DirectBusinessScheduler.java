// BusinessScheduler 缺省实现：调用线程直执行（测试友好；Paper 侧替换为主线程投递）
package com.kurobridge.pure.core.server;

/** 直执行实现（无排队、无切线程）。 */
public final class DirectBusinessScheduler implements BusinessScheduler {

    public static final DirectBusinessScheduler INSTANCE = new DirectBusinessScheduler();

    private DirectBusinessScheduler() {}

    @Override
    public void dispatch(Runnable task) {
        task.run();
    }
}
