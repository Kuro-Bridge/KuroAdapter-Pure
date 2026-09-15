// 会话环境：PeerSession 依赖的服务端身份/鉴权/绑定快照/调度器集合（依赖注入包）
package com.kurobridge.pure.core.server;

import java.util.List;
import java.util.function.Supplier;

/**
 * 会话环境（每服务端一份，全部连接共享；token 进程生命周期固定——对齐主仓语义）。
 */
public record SessionContext(
        String serverId,
        String serverVersion,
        String token,
        Supplier<List<String>> channelBindings,
        ServerTimeouts timeouts,
        TimeoutScheduler scheduler,
        BusinessScheduler businessScheduler,
        BusinessHooks hooks,
        KbLogger logger) {}
