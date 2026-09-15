// 配置形状：对齐主仓 docs/config-schema.md 但去掉 runtime 与 embedded 段（纯 Java 线无子进程/无嵌入）
package com.kurobridge.pure.core.business;

import java.util.List;

/**
 * 配置模型：channels（必填可空，去重保序）、token（缺省 "" = 不鉴权）、admins（缺省 []，
 * channel 与 users 各自去重保序）、ws（整段缺省 = null，即动态端口 + 全部接口）。
 */
public record PureConfig(List<String> channels, String token, List<AdminMapping> admins, WsListen ws) {

    /** 群管理员映射：channel 命中且 userId 在 users 内 → command 放行。 */
    public record AdminMapping(String channel, List<String> users) {}

    /** WS 监听段：host null = 全部接口；port null = 动态端口（只配 host 合法）。 */
    public record WsListen(String host, Integer port) {}

    public PureConfig {
        channels = List.copyOf(channels);
        admins = List.copyOf(admins);
    }

    /** 默认配置：无绑定、不鉴权、无管理员、无 ws 段（平台消息不进游戏，直到服主写入绑定）。 */
    public static PureConfig defaults() {
        return new PureConfig(List.of(), "", List.of(), null);
    }
}
