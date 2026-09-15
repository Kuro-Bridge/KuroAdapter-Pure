// 日志抽象（依赖注入：core 不绑定具体实现；测试用收集器，生产默认 JUL——Paper 会桥接）
package com.kurobridge.pure.core.server;

/** core 侧日志接口（对齐主仓 transport.ts 的 Logger 注入法）。 */
public interface KbLogger {

    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message, Throwable error);
}
