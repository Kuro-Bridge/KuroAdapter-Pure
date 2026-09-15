// 服务端超时阈值（毫秒）；0 = 禁用对应检测
package com.kurobridge.pure.core.server;

/** hello 等待上限与空闲检测阈值。 */
public record ServerTimeouts(long helloTimeoutMs, long idleTimeoutMs) {

    public static final long DEFAULT_HELLO_TIMEOUT_MS = 10_000;

    public static final long DEFAULT_IDLE_TIMEOUT_MS = 30_000;

    public static ServerTimeouts defaults() {
        return new ServerTimeouts(DEFAULT_HELLO_TIMEOUT_MS, DEFAULT_IDLE_TIMEOUT_MS);
    }
}
