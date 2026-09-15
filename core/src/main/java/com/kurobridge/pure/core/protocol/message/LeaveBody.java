// 玩家退服事件体
package com.kurobridge.pure.core.protocol.message;

/** 退服 body（channel/playerName 均非空）。 */
public record LeaveBody(String channel, String playerName) {}
