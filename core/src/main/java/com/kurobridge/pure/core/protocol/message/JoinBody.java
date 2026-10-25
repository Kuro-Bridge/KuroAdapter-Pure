// 玩家进服/退服事件体
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 进服 body（channel/playerName 均非空）。 */
public final class JoinBody {

    private final String channel;
    private final String playerName;

    public JoinBody(String channel, String playerName) {
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
        if (!(obj instanceof JoinBody)) {
            return false;
        }
        JoinBody other = (JoinBody) obj;
        return Objects.equals(channel, other.channel) && Objects.equals(playerName, other.playerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, playerName);
    }

    @Override
    public String toString() {
        return "JoinBody[channel=" + channel + ", playerName=" + playerName + "]";
    }
}
