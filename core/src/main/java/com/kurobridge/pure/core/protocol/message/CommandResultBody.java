// command_result 响应体（ok 体 output 可缺省 = 无输出；error 体 error 非空）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;
import java.util.Objects;

/** 命令执行结果 body（工厂方法命名 success/failure，避免与组件访问器 ok() 撞名）。 */
public final class CommandResultBody {

    private final boolean ok;
    private final List<String> output;
    private final String error;

    public CommandResultBody(boolean ok, List<String> output, String error) {
        this.ok = ok;
        this.output = output;
        this.error = error;
    }

    public static CommandResultBody success(List<String> output) {
        return new CommandResultBody(true, output, null);
    }

    public static CommandResultBody success() {
        return success(null);
    }

    public static CommandResultBody failure(String error) {
        return new CommandResultBody(false, null, error);
    }

    public boolean ok() {
        return ok;
    }

    public List<String> output() {
        return output;
    }

    public String error() {
        return error;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CommandResultBody)) {
            return false;
        }
        CommandResultBody other = (CommandResultBody) obj;
        return ok == other.ok && Objects.equals(output, other.output) && Objects.equals(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, output, error);
    }

    @Override
    public String toString() {
        return "CommandResultBody[ok=" + ok + ", output=" + output + ", error=" + error + "]";
    }
}
