// reload 回执分组（D6 热/冷矩阵）：新旧配置机械对比，纯函数可测（文案即字段名）
package com.kurobridge.pure.core.business;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 新旧配置对比报告（reload 回执的唯一事实来源，D6 热/冷矩阵）：
 *
 * <ul>
 *   <li>hotApplied：可热更且发生了变化的字段（channels → 绑定表 replace 自动触发 bindings_updated
 *       广播；admins → AdminTable 重建原子换入）；
 *   <li>restartPending：冷字段中有变化的（token / ws.host / ws.port / server.id，进程固定，重启生效）。
 * </ul>
 */
public record ConfigReloadReport(List<String> hotApplied, List<String> restartPending) {

    public ConfigReloadReport {
        hotApplied = List.copyOf(hotApplied);
        restartPending = List.copyOf(restartPending);
    }

    /** 机械对比新旧配置（ws 整段缺省按 host/port 均 null 计）。 */
    public static ConfigReloadReport diff(PureConfig previous, PureConfig next) {
        List<String> hot = new ArrayList<>();
        if (!previous.channels().equals(next.channels())) {
            hot.add("绑定频道（bindings_updated 已广播）");
        }
        if (!previous.admins().equals(next.admins())) {
            hot.add("管理员表");
        }

        List<String> pending = new ArrayList<>();
        if (!previous.token().equals(next.token())) {
            pending.add("token");
        }
        pending.addAll(wsDiff(previous.ws(), next.ws()));
        if (!previous.serverId().equals(next.serverId())) {
            pending.add("server.id");
        }
        return new ConfigReloadReport(hot, pending);
    }

    private static List<String> wsDiff(PureConfig.WsListen previous, PureConfig.WsListen next) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(hostOf(previous), hostOf(next))) {
            changed.add("ws.host");
        }
        if (!Objects.equals(portOf(previous), portOf(next))) {
            changed.add("ws.port");
        }
        return changed;
    }

    private static String hostOf(PureConfig.WsListen ws) {
        return ws == null ? null : ws.host();
    }

    private static Integer portOf(PureConfig.WsListen ws) {
        return ws == null ? null : ws.port();
    }
}
