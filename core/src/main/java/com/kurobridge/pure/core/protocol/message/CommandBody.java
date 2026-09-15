// command 请求体（管理员判定在服务端；执行者名义 = 服务器控制台）
package com.kurobridge.pure.core.protocol.message;

/** command body：command 非空且不含前导斜杠；source.{channel,userId} 均非空。 */
public record CommandBody(String command, CommandSource source) {}
