// command_result 响应体（ok 体 output 可缺省 = 无输出；error 体 error 非空）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;

/** 命令执行结果 body（工厂方法命名 success/failure，避免与组件访问器 ok() 撞名）。 */
public record CommandResultBody(boolean ok, List<String> output, String error) {

    public static CommandResultBody success(List<String> output) {
        return new CommandResultBody(true, output, null);
    }

    public static CommandResultBody success() {
        return success(null);
    }

    public static CommandResultBody failure(String error) {
        return new CommandResultBody(false, null, error);
    }
}
