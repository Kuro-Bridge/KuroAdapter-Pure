// 玩家进服/退服事件体
package com.kurobridge.pure.core.protocol.message;

/** 进服 body（channel/playerName 均非空）。 */
public record JoinBody(String channel, String playerName) {}
