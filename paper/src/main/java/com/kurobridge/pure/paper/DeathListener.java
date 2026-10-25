// 玩家死亡事件桥接：deathMessage 为 null（/kill 等场景）时空串兜底（协议允许 message 空串）
package com.kurobridge.pure.paper;

import java.util.regex.Pattern;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * 玩家死亡事件桥接（对齐主仓 DeathListener，ADR-038 universal 化）：PlayerDeathEvent →
 * getDeathMessage() String → 剥 legacy 颜色码 → 上报对端。
 *
 * <p>String 取值路径经 paper-api 全版本存留（1.12.2 → 1.21.4/26.x），不再走 Component
 * 序列化——旧版取值在 Paper 侧经 legacy 序列化，可能带 {@code §} 颜色码，
 * {@link #LEGACY_COLOR} 剥离后即为纯文本。主线程事件，逐频道 fan-out 由 PaperRelay 决定。
 */
public final class DeathListener implements Listener {

    /** legacy 颜色码 §X / §xX（大小写通吃），死亡消息取 String 路径时可能出现 */
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\u00a7[0-9A-FK-ORX]");

    private final PaperRelay relay;

    public DeathListener(PaperRelay relay) {
        this.relay = relay;
    }

    @SuppressWarnings("deprecation") // getDeathMessage()：String 取值 1.12.2 → 26.3 全版本存留（ADR-038）
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String deathMessage = event.getDeathMessage();
        String text =
                deathMessage == null ? "" : LEGACY_COLOR.matcher(deathMessage).replaceAll("");
        relay.playerDeath(event.getEntity().getName(), text);
    }
}
