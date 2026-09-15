// command 请求体（管理员判定在服务端；执行者名义 = 服务器控制台）
package com.kurobridge.pure.core.protocol.message;

/** command body：command 非空（min(1)，对齐 zod SSOT；前导斜杠是对端约定，peer-guide 说明，非本层校验）；source.{channel,userId} 均非空。 */
public record CommandBody(String command, CommandSource source) {}
