// status 快照在线数口径纯函数（quit 窗口按 UUID 剔除退出者后计数，双向稳健）
package com.kurobridge.pure.core.server;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * status 快照在线数口径：Paper 的 {@code PlayerQuitEvent} 触发时退出者尚未移出
 * {@code Bukkit.getOnlinePlayers()} 列表（SMOKE-2026-09-18-2 §6-3 真机实证：leave 后
 * onlinePlayers:1 实际 0），quit 路径须按 UUID 剔除退出者后计数；退出者已不在列表
 * （被先行移除/未来语义变化）则不重复剔除——双向稳健。裁决见
 * docs/history/GAPS-2026-09-19.md §2.1。
 */
public final class StatusCounts {

    private StatusCounts() {}

    /**
     * 统计 onlineIds 中不等于 leavingId 的元素个数——即 quit 后的真实在线数。
     *
     * @param onlineIds quit 事件后现取的在线玩家 UUID 集合（Paper quit 窗口内仍含退出者）
     * @param leavingId 退出者 UUID（quit 事件必有玩家，null 属编程错误，前置拒绝）
     * @return 剔除退出者后的在线数；退出者不在列表则原样计数（不重复剔除）
     */
    public static int countExcluding(Collection<UUID> onlineIds, UUID leavingId) {
        Objects.requireNonNull(leavingId, "leavingId");
        int count = 0;
        for (UUID id : onlineIds) {
            if (!id.equals(leavingId)) {
                count++;
            }
        }
        return count;
    }
}
