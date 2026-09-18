// 游戏聊天事件桥接：AsyncChatEvent（异步线程）→ kurobridge.relay 权限静音出口 → 逐频道出帧
package com.kurobridge.pure.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 聊天监听（主仓 ChatListener 同语义）：无 {@code kurobridge.relay} 权限（含 negate）→
 * 该玩家聊天不上报（静音）；转发到哪些频道由绑定表决定（PaperRelay fan-out）。
 * AsyncChatEvent 在异步线程触发，出帧经 PureWsServer.broadcast（线程安全）。
 */
public final class ChatListener implements Listener {

    private final PaperRelay relay;

    public ChatListener(PaperRelay relay) {
        this.relay = relay;
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        if (!event.getPlayer().hasPermission("kurobridge.relay")) {
            return; // 无 kurobridge.relay 权限（negate 即静音）：该玩家聊天不上报
        }
        relay.gameChat(event.getPlayer().getName(), PlainText.serialize(event.message()));
    }
}
