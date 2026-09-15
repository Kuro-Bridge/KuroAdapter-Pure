// pong 响应体（心跳应答；timestamp 原样回显）
package com.kurobridge.pure.core.protocol.message;

/** pong body。 */
public record PongBody(long timestamp) {}
