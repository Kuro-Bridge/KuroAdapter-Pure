// 平台→游戏聊天事件体（方向区分：sender，与游戏方向的 playerName 同 type 不同 body）
package com.kurobridge.pure.core.protocol.message;

/** 平台聊天 body（channel/sender/content 均非空）。 */
public record PlatformChatBody(String channel, String sender, String content) {}
