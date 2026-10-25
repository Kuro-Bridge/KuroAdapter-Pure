package com.kurobridge.pure.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

// AsyncPlayerChatEvent 用全限定名引用——import 行的弃用警告不受类级 @SuppressWarnings
// 覆盖，会触发 -Werror（见下方 handler 签名）。

/**
 * 旧版聊天事件桥接（对齐主仓 ADR-038 universal 基线）：AsyncPlayerChatEvent
 * （1.12.2 ~ 1.18.2 唯一聊天事件）→ getMessage() String → 上报对端。仅当
 * {@link Compat#asyncChatEventPresent()} 为假（无 AsyncChatEvent）时注册——现代服上挂本监听
 * 会把整服聊天管线降级为 legacy 模式。
 *
 * <p>语义与 {@link ChatListener} 完全一致（kurobridge.relay 权限静音），区别仅在事件类型与
 * 消息载体（String，无需序列化）。异步线程事件，出帧经 PureWsServer.broadcast（线程安全）。
 */
@SuppressWarnings("deprecation") // AsyncPlayerChatEvent：1.19 起被 AsyncChatEvent 取代，旧服仍需
public final class LegacyChatListener implements Listener {

    private final PaperRelay relay;

    public LegacyChatListener(PaperRelay relay) {
        this.relay = relay;
    }

    @EventHandler
    public void onAsyncPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (!event.getPlayer().hasPermission("kurobridge.relay")) {
            return; // 无 kurobridge.relay 权限（negate 即静音）：该玩家聊天不上报
        }
        relay.gameChat(event.getPlayer().getName(), event.getMessage());
    }
}
