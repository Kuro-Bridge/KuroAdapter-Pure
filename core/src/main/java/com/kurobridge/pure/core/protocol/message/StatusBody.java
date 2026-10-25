// 服务器状态事件体（无 channel——全服状态；事件驱动推送，无周期上报）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 状态 body：tps 为任意非负数值；onlinePlayers/uptimeSeconds 为非负整数。 */
public final class StatusBody {

    private final double tps;
    private final long onlinePlayers;
    private final long uptimeSeconds;

    public StatusBody(double tps, long onlinePlayers, long uptimeSeconds) {
        this.tps = tps;
        this.onlinePlayers = onlinePlayers;
        this.uptimeSeconds = uptimeSeconds;
    }

    public double tps() {
        return tps;
    }

    public long onlinePlayers() {
        return onlinePlayers;
    }

    public long uptimeSeconds() {
        return uptimeSeconds;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StatusBody)) {
            return false;
        }
        StatusBody other = (StatusBody) obj;
        return Double.doubleToLongBits(tps) == Double.doubleToLongBits(other.tps)
                && onlinePlayers == other.onlinePlayers
                && uptimeSeconds == other.uptimeSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tps, onlinePlayers, uptimeSeconds);
    }

    @Override
    public String toString() {
        return "StatusBody[tps=" + tps + ", onlinePlayers=" + onlinePlayers + ", uptimeSeconds=" + uptimeSeconds + "]";
    }
}
