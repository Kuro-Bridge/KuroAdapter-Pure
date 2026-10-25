// command 请求体（管理员判定在服务端；执行者名义 = 服务器控制台）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** command body：command 非空（min(1)，对齐 zod SSOT；前导斜杠是对端约定，peer-guide 说明，非本层校验）；source.{channel,userId} 均非空。 */
public final class CommandBody {

    private final String command;
    private final CommandSource source;

    public CommandBody(String command, CommandSource source) {
        this.command = command;
        this.source = source;
    }

    public String command() {
        return command;
    }

    public CommandSource source() {
        return source;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CommandBody)) {
            return false;
        }
        CommandBody other = (CommandBody) obj;
        return Objects.equals(command, other.command) && Objects.equals(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, source);
    }

    @Override
    public String toString() {
        return "CommandBody[command=" + command + ", source=" + source + "]";
    }
}
