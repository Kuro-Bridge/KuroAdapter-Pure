// query 请求体（kind 枚举 status | bindings）
package com.kurobridge.pure.core.protocol.message;

/** 查询 body。 */
public record QueryBody(String kind) {

    public static final String KIND_STATUS = "status";
    public static final String KIND_BINDINGS = "bindings";
}
