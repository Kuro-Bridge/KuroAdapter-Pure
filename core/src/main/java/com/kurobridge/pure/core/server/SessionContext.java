// 会话环境：PeerSession 依赖的服务端身份/鉴权/绑定快照/调度器集合（依赖注入包）
package com.kurobridge.pure.core.server;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 会话环境（每服务端一份，全部连接共享；token 进程生命周期固定——对齐主仓语义）。
 */
public final class SessionContext {

    private final String serverId;
    private final String serverVersion;
    private final String token;
    private final Supplier<List<String>> channelBindings;
    private final ServerTimeouts timeouts;
    private final TimeoutScheduler scheduler;
    private final BusinessScheduler businessScheduler;
    private final BusinessHooks hooks;
    private final KbLogger logger;

    public SessionContext(
            String serverId,
            String serverVersion,
            String token,
            Supplier<List<String>> channelBindings,
            ServerTimeouts timeouts,
            TimeoutScheduler scheduler,
            BusinessScheduler businessScheduler,
            BusinessHooks hooks,
            KbLogger logger) {
        this.serverId = serverId;
        this.serverVersion = serverVersion;
        this.token = token;
        this.channelBindings = channelBindings;
        this.timeouts = timeouts;
        this.scheduler = scheduler;
        this.businessScheduler = businessScheduler;
        this.hooks = hooks;
        this.logger = logger;
    }

    public String serverId() {
        return serverId;
    }

    public String serverVersion() {
        return serverVersion;
    }

    public String token() {
        return token;
    }

    public Supplier<List<String>> channelBindings() {
        return channelBindings;
    }

    public ServerTimeouts timeouts() {
        return timeouts;
    }

    public TimeoutScheduler scheduler() {
        return scheduler;
    }

    public BusinessScheduler businessScheduler() {
        return businessScheduler;
    }

    public BusinessHooks hooks() {
        return hooks;
    }

    public KbLogger logger() {
        return logger;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SessionContext)) {
            return false;
        }
        SessionContext other = (SessionContext) obj;
        return Objects.equals(serverId, other.serverId)
                && Objects.equals(serverVersion, other.serverVersion)
                && Objects.equals(token, other.token)
                && Objects.equals(channelBindings, other.channelBindings)
                && Objects.equals(timeouts, other.timeouts)
                && Objects.equals(scheduler, other.scheduler)
                && Objects.equals(businessScheduler, other.businessScheduler)
                && Objects.equals(hooks, other.hooks)
                && Objects.equals(logger, other.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                serverId, serverVersion, token, channelBindings, timeouts, scheduler, businessScheduler, hooks, logger);
    }

    @Override
    public String toString() {
        return "SessionContext[serverId=" + serverId + ", serverVersion=" + serverVersion + "]";
    }
}
