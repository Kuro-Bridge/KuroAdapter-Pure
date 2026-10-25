// 配置形状：对齐主仓 docs/config-schema.md 但去掉 runtime 与 embedded 段（纯 Java 线无子进程/无嵌入）
package com.kurobridge.pure.core.business;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 配置模型：channels（必填可空，去重保序）、token（缺省 "" = 不鉴权）、admins（缺省 []，
 * channel 与 users 各自去重保序）、ws（整段缺省 = null，即动态端口 + 全部接口）、
 * serverId（server.id 缺省 {@link #DEFAULT_SERVER_ID}，进程固定，改后须重启）。
 */
public final class PureConfig {

    /** server.id 缺省值（与主仓 ADR-034 字段形状对齐，默认值保留 Pure 运行时辨识度）。 */
    public static final String DEFAULT_SERVER_ID = "kurobridge-pure";

    private final List<String> channels;
    private final String token;
    private final List<AdminMapping> admins;
    private final WsListen ws;
    private final String serverId;

    public PureConfig(List<String> channels, String token, List<AdminMapping> admins, WsListen ws, String serverId) {
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.token = token;
        this.admins = Collections.unmodifiableList(new ArrayList<>(admins));
        this.ws = ws;
        this.serverId = serverId == null ? DEFAULT_SERVER_ID : serverId; // 空串由 ConfigLoader 解析期拒绝，此处只兜 null
    }

    /** 解析层缺省：无绑定、不鉴权、无管理员、无 ws 段、serverId 默认（首启安全默认见 ConfigBootstrap）。 */
    public static PureConfig defaults() {
        return new PureConfig(Collections.emptyList(), "", Collections.emptyList(), null, DEFAULT_SERVER_ID);
    }

    public List<String> channels() {
        return channels;
    }

    public String token() {
        return token;
    }

    public List<AdminMapping> admins() {
        return admins;
    }

    public WsListen ws() {
        return ws;
    }

    public String serverId() {
        return serverId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PureConfig)) {
            return false;
        }
        PureConfig other = (PureConfig) obj;
        return Objects.equals(channels, other.channels)
                && Objects.equals(token, other.token)
                && Objects.equals(admins, other.admins)
                && Objects.equals(ws, other.ws)
                && Objects.equals(serverId, other.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channels, token, admins, ws, serverId);
    }

    @Override
    public String toString() {
        return "PureConfig[channels=" + channels + ", token=" + token + ", admins=" + admins + ", ws=" + ws
                + ", serverId=" + serverId + "]";
    }

    /** 群管理员映射：channel 命中且 userId 在 users 内 → command 放行。 */
    public static final class AdminMapping {

        private final String channel;
        private final List<String> users;

        public AdminMapping(String channel, List<String> users) {
            this.channel = channel;
            this.users = users;
        }

        public String channel() {
            return channel;
        }

        public List<String> users() {
            return users;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AdminMapping)) {
                return false;
            }
            AdminMapping other = (AdminMapping) obj;
            return Objects.equals(channel, other.channel) && Objects.equals(users, other.users);
        }

        @Override
        public int hashCode() {
            return Objects.hash(channel, users);
        }

        @Override
        public String toString() {
            return "AdminMapping[channel=" + channel + ", users=" + users + "]";
        }
    }

    /** WS 监听段：host null = 全部接口；port null = 动态端口（只配 host 合法）。 */
    public static final class WsListen {

        private final String host;
        private final Integer port;

        public WsListen(String host, Integer port) {
            this.host = host;
            this.port = port;
        }

        public String host() {
            return host;
        }

        public Integer port() {
            return port;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof WsListen)) {
                return false;
            }
            WsListen other = (WsListen) obj;
            return Objects.equals(host, other.host) && Objects.equals(port, other.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port);
        }

        @Override
        public String toString() {
            return "WsListen[host=" + host + ", port=" + port + "]";
        }
    }
}
