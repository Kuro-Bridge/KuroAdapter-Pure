// 管理员判定表：channel 命中且 userId 在 users 内 → command 放行（主仓 AdminTable 同语义）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.business.PureConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员映射（数据源 PureConfig.admins，ConfigLoader 已按 channel 去重保序）。
 * 判定规则（peer-guide §5.1 / 主仓 admins.ts）：source.channel 命中某条目且 source.userId
 * 在其 users 内 → 放行。表本身不可变；热重载 = 按新配置重建实例，经
 * {@code PaperRelay.replaceAdmins} 原子换入（volatile）。
 */
public final class AdminTable {

    private final Map<String, Set<String>> usersByChannel;

    public AdminTable(List<PureConfig.AdminMapping> admins) {
        Map<String, Set<String>> byChannel = new LinkedHashMap<>();
        for (PureConfig.AdminMapping mapping : admins) {
            byChannel.putIfAbsent(mapping.channel(), Set.copyOf(mapping.users()));
        }
        this.usersByChannel = Map.copyOf(byChannel);
    }

    /** 是否管理员（command 帧的放行判定；false = forbidden 不 dispatch）。 */
    public boolean isAdmin(String channel, String userId) {
        Set<String> users = usersByChannel.get(channel);
        return users != null && users.contains(userId);
    }
}
