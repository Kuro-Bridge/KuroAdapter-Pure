// 命令来源（channel + userId，协议端如实提取；管理员判定在服务端）
package com.kurobridge.pure.core.protocol.message;

/** command.source：channel 与 userId 均非空。 */
public record CommandSource(String channel, String userId) {}
