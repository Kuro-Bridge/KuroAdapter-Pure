// 服务器状态事件体（无 channel——全服状态；事件驱动推送，无周期上报）
package com.kurobridge.pure.core.protocol.message;

/** 状态 body：tps 为任意非负数值；onlinePlayers/uptimeSeconds 为非负整数。 */
public record StatusBody(double tps, long onlinePlayers, long uptimeSeconds) {}
