// 玩家死亡事件桥接：deathMessage 为 null（/kill 等场景）时空串兜底（协议允许 message 空串）
package com.kurobridge.pure.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** 死亡监听（主仓 DeathListener 同语义）；主线程事件，逐频道 fan-out 由 PaperRelay 决定。 */
public final class DeathListener implements Listener {

    private final PaperRelay relay;

    public DeathListener(PaperRelay relay) {
        this.relay = relay;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Component deathMessage = event.deathMessage();
        relay.playerDeath(event.getEntity().getName(), deathMessage == null ? "" : PlainText.serialize(deathMessage));
    }
}
