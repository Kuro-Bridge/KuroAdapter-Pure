// 业务回调集（协议层 → 业务层的事件入口；本阶段业务骨架提供空实现，测试提供 fake）
package com.kurobridge.pure.core.server;

import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.PlatformChatBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 业务入口（对齐主仓 server.ts 的 onPlatformChat/onCommand 注册位 + status 缓存）。
 * 回调在 BusinessScheduler 派发的线程上执行（协议应答不经过这里）。
 */
public interface BusinessHooks {

    /** 平台→游戏聊天（绑定表过滤在业务层）。 */
    void onPlatformChat(PlatformChatBody body);

    /**
     * 群指令请求（管理员判定 + 执行在业务层）。
     * 返回 null = 业务处理未注册（协议层回执 command handler not available）。
     */
    CompletableFuture<CommandResultBody> onCommand(CommandBody body);

    /** 最近一帧服务器状态快照（query kind=status 本地作答；空 = 尚未收到过 → no status yet）。 */
    Optional<StatusBody> latestStatus();
}
