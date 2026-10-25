// 玩家死亡事件体（字段名是 player 不是 playerName——历史原因；message 允许空串）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 死亡 body（channel/player 非空；message 任意字符串，deathMessage 为 null 时空串兜底）。 */
public final class DeathBody {

    private final String channel;
    private final String player;
    private final String message;

    public DeathBody(String channel, String player, String message) {
        this.channel = channel;
        this.player = player;
        this.message = message;
    }

    public String channel() {
        return channel;
    }

    public String player() {
        return player;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeathBody)) {
            return false;
        }
        DeathBody other = (DeathBody) obj;
        return Objects.equals(channel, other.channel)
                && Objects.equals(player, other.player)
                && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, player, message);
    }

    @Override
    public String toString() {
        return "DeathBody[channel=" + channel + ", player=" + player + ", message=" + message + "]";
    }
}
