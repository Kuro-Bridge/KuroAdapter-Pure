// 玩家进出服事件桥接 + status 快照数据源（进出服后各补一帧，在线数变化点即推送点）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.protocol.message.StatusBody;
import com.kurobridge.pure.core.server.StatusCounts;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 进退服监听（主仓 ConnectionListener 同语义）：join/quit 各一帧事件 + 一帧 status 快照。
 * 主线程事件；status 数据 = 1 分钟 TPS（clamp ≥0 保留 1 位小数）+ 在线数 + JVM uptime。
 *
 * <p>两窗口在线数语义（均为「事件后现取」，Paper 行为，SMOKE-2026-09-18-2 §6-3 真机实证）：
 * join 事件触发时新玩家已入 {@code getOnlinePlayers()} 列表，直接现取计数；quit 事件触发时
 * 退出者尚未出列，按 UUID 剔除后计数——推送值 = 事件后的真实在线数（裁决见
 * docs/history/GAPS-2026-09-19.md §2.1）。
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
        relay.pushStatus(statusSnapshotAfterQuit(event.getPlayer().getUniqueId()));
    }

    /** join 后的状态快照（新玩家已入在线列表，现取即含新玩家的真实值）。 */
    private static StatusBody statusSnapshot() {
        return new StatusBody(currentTps(), Bukkit.getOnlinePlayers().size(), uptimeSeconds());
    }

    /**
     * quit 后的状态快照：退出者尚未移出在线列表（Paper 语义，SMOKE-2026-09-18-2 §6-3 实证），
     * 现取 UUID 后按 {@link StatusCounts#countExcluding} 剔除退出者计数——推送值 = 离开后
     * 真实在线数；退出者已不在列表则不重复剔除（双向稳健）。
     */
    private static StatusBody statusSnapshotAfterQuit(UUID excluding) {
        List<UUID> onlineIds =
                Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        return new StatusBody(currentTps(), StatusCounts.countExcluding(onlineIds, excluding), uptimeSeconds());
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
