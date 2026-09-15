// 玩家死亡事件体（字段名是 player 不是 playerName——历史原因；message 允许空串）
package com.kurobridge.pure.core.protocol.message;

/** 死亡 body（channel/player 非空；message 任意字符串，deathMessage 为 null 时空串兜底）。 */
public record DeathBody(String channel, String player, String message) {}
