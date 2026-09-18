// 玩家进出服事件桥接 + status 快照数据源（进出服后各补一帧，在线数变化点即推送点）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.lang.management.ManagementFactory;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 进退服监听（主仓 ConnectionListener 同语义）：join/quit 各一帧事件 + 一帧 status 快照。
 * 主线程事件；status 数据 = 1 分钟 TPS（clamp ≥0 保留 1 位小数）+ 在线数 + JVM uptime。
 */
public final class ConnectionListener implements Listener {

    private final PaperRelay relay;

    public ConnectionListener(PaperRelay relay) {
        this.relay = relay;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        relay.playerJoin(event.getPlayer().getName());
        relay.pushStatus(statusSnapshot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        relay.playerLeave(event.getPlayer().getName());
        relay.pushStatus(statusSnapshot());
    }

    /** 进出服后的状态快照（在线数已变化，对端按事件顺序处理即得新值）。 */
    private static StatusBody statusSnapshot() {
        return new StatusBody(currentTps(), Bukkit.getOnlinePlayers().size(), uptimeSeconds());
    }

    /** 1 分钟窗口 TPS（Paper API）；clamp 到 >=0 并保留 1 位小数（协议要求 nonnegative）。 */
    private static double currentTps() {
        double tps = Bukkit.getTPS()[0];
        return Math.max(0, Math.round(tps * 10.0) / 10.0);
    }

    /** JVM uptime（JDK 标准接口），等价专用服场景下的服务器 uptime。 */
    private static long uptimeSeconds() {
        return ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }
}
