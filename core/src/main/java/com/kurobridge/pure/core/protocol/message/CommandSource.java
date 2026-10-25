// 命令来源（channel + userId，协议端如实提取；管理员判定在服务端）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** command.source：channel 与 userId 均非空。 */
public final class CommandSource {

    private final String channel;
    private final String userId;

    public CommandSource(String channel, String userId) {
        this.channel = channel;
        this.userId = userId;
    }

    public String channel() {
        return channel;
    }

    public String userId() {
        return userId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CommandSource)) {
            return false;
        }
        CommandSource other = (CommandSource) obj;
        return Objects.equals(channel, other.channel) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, userId);
    }

    @Override
    public String toString() {
        return "CommandSource[channel=" + channel + ", userId=" + userId + "]";
    }
}
