// 游戏→平台聊天事件体（方向区分：playerName；服务端按绑定频道逐频道 fan-out）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 游戏聊天 body（channel/playerName/content 均非空）。 */
public final class GameChatBody {

    private final String channel;
    private final String playerName;
    private final String content;

    public GameChatBody(String channel, String playerName, String content) {
        this.channel = channel;
        this.playerName = playerName;
        this.content = content;
    }

    public String channel() {
        return channel;
    }

    public String playerName() {
        return playerName;
    }

    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GameChatBody)) {
            return false;
        }
        GameChatBody other = (GameChatBody) obj;
        return Objects.equals(channel, other.channel)
                && Objects.equals(playerName, other.playerName)
                && Objects.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, playerName, content);
    }

    @Override
    public String toString() {
        return "GameChatBody[channel=" + channel + ", playerName=" + playerName + ", content=" + content + "]";
    }
}
