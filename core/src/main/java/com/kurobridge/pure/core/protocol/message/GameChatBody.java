// 游戏→平台聊天事件体（方向区分：playerName；服务端按绑定频道逐频道 fan-out）
package com.kurobridge.pure.core.protocol.message;

/** 游戏聊天 body（channel/playerName/content 均非空）。 */
public record GameChatBody(String channel, String playerName, String content) {}
