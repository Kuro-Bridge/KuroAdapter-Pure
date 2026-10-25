// 平台→游戏聊天事件体（方向区分：sender，与游戏方向的 playerName 同 type 不同 body）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** 平台聊天 body（channel/sender/content 均非空）。 */
public final class PlatformChatBody {

    private final String channel;
    private final String sender;
    private final String content;

    public PlatformChatBody(String channel, String sender, String content) {
        this.channel = channel;
        this.sender = sender;
        this.content = content;
    }

    public String channel() {
        return channel;
    }

    public String sender() {
        return sender;
    }

    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PlatformChatBody)) {
            return false;
        }
        PlatformChatBody other = (PlatformChatBody) obj;
        return Objects.equals(channel, other.channel)
                && Objects.equals(sender, other.sender)
                && Objects.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, sender, content);
    }

    @Override
    public String toString() {
        return "PlatformChatBody[channel=" + channel + ", sender=" + sender + ", content=" + content + "]";
    }
}
