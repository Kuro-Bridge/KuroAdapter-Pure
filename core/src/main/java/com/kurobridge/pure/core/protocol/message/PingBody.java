// ping 请求体（心跳；timestamp 整数 >=0，pong 原样回显）
package com.kurobridge.pure.core.protocol.message;

/** ping body。 */
public record PingBody(long timestamp) {}
