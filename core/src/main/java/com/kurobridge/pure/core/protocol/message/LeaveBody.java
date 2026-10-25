// 玩家退服事件体
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 退服 body（channel/playerName 均非空）。 */
public final class LeaveBody {

    private final String channel;
    private final String playerName;

    public LeaveBody(String channel, String playerName) {
        this.channel = channel;
        this.playerName = playerName;
    }

    public String channel() {
        return channel;
    }

    public String playerName() {
        return playerName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LeaveBody)) {
            return false;
        }
        LeaveBody other = (LeaveBody) obj;
        return Objects.equals(channel, other.channel) && Objects.equals(playerName, other.playerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, playerName);
    }

    @Override
    public String toString() {
        return "LeaveBody[channel=" + channel + ", playerName=" + playerName + "]";
    }
}
