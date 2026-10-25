// 服务端超时阈值（毫秒）；0 = 禁用对应检测
package com.kurobridge.pure.core.server;

import java.util.Objects;

/** hello 等待上限与空闲检测阈值。 */
public final class ServerTimeouts {

    public static final long DEFAULT_HELLO_TIMEOUT_MS = 10_000;

    public static final long DEFAULT_IDLE_TIMEOUT_MS = 30_000;

    private final long helloTimeoutMs;
    private final long idleTimeoutMs;

    public ServerTimeouts(long helloTimeoutMs, long idleTimeoutMs) {
        this.helloTimeoutMs = helloTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public static ServerTimeouts defaults() {
        return new ServerTimeouts(DEFAULT_HELLO_TIMEOUT_MS, DEFAULT_IDLE_TIMEOUT_MS);
    }

    public long helloTimeoutMs() {
        return helloTimeoutMs;
    }

    public long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ServerTimeouts)) {
            return false;
        }
        ServerTimeouts other = (ServerTimeouts) obj;
        return helloTimeoutMs == other.helloTimeoutMs && idleTimeoutMs == other.idleTimeoutMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(helloTimeoutMs, idleTimeoutMs);
    }

    @Override
    public String toString() {
        return "ServerTimeouts[helloTimeoutMs=" + helloTimeoutMs + ", idleTimeoutMs=" + idleTimeoutMs + "]";
    }
}
