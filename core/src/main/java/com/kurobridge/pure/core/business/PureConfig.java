// 配置形状：对齐主仓 docs/config-schema.md 但去掉 runtime 与 embedded 段（纯 Java 线无子进程/无嵌入）
package com.kurobridge.pure.core.business;

import java.util.List;

/**
 * 配置模型：channels（必填可空，去重保序）、token（缺省 "" = 不鉴权）、admins（缺省 []，
 * channel 与 users 各自去重保序）、ws（整段缺省 = null，即动态端口 + 全部接口）、
 * serverId（server.id 缺省 {@link #DEFAULT_SERVER_ID}，进程固定，改后须重启）。
 */
public record PureConfig(List<String> channels, String token, List<AdminMapping> admins, WsListen ws, String serverId) {

    /** server.id 缺省值（与主仓 ADR-034 字段形状对齐，默认值保留 Pure 运行时辨识度）。 */
    public static final String DEFAULT_SERVER_ID = "kurobridge-pure";

    /** 群管理员映射：channel 命中且 userId 在 users 内 → command 放行。 */
    public record AdminMapping(String channel, List<String> users) {}

    /** WS 监听段：host null = 全部接口；port null = 动态端口（只配 host 合法）。 */
    public record WsListen(String host, Integer port) {}

    public PureConfig {
        channels = List.copyOf(channels);
        admins = List.copyOf(admins);
        serverId = serverId == null ? DEFAULT_SERVER_ID : serverId; // 空串由 ConfigLoader 解析期拒绝，此处只兜 null
    }

    /** 解析层缺省：无绑定、不鉴权、无管理员、无 ws 段、serverId 默认（首启安全默认见 ConfigBootstrap）。 */
    public static PureConfig defaults() {
        return new PureConfig(List.of(), "", List.of(), null, DEFAULT_SERVER_ID);
    }
}
