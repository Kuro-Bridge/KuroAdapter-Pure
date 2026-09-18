// :paper 业务装配：游戏事件按绑定频道 fan-out 出帧 + BusinessHooks 入站 + status 快照单一来源
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.business.BindingStore;
import com.kurobridge.pure.core.business.ForwardRules;
import com.kurobridge.pure.core.protocol.Frame;
import com.kurobridge.pure.core.protocol.Frames;
import com.kurobridge.pure.core.protocol.message.BindingsUpdatedBody;
import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.DeathBody;
import com.kurobridge.pure.core.protocol.message.GameChatBody;
import com.kurobridge.pure.core.protocol.message.JoinBody;
import com.kurobridge.pure.core.protocol.message.LeaveBody;
import com.kurobridge.pure.core.protocol.message.PlatformChatBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import com.kurobridge.pure.core.server.BusinessHooks;
import com.kurobridge.pure.core.server.KbLogger;
import com.kurobridge.pure.core.server.PureWsServer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/**
 * 纯 Java 线的 Relay（对齐主仓 bridge/core/src/relay.ts 的装配语义）：
 *
 * - 游戏→平台：监听器上报原始事件，按绑定表逐频道出帧广播给全部已握手对端
 *   （主仓 fanoutGameEvent + sendToEstablished 的合体；无绑定/无对端不出帧）；
 * - status：更新快照与推送同一入口 {@link #pushStatus}（镜像主仓 server.sendStatus，
 *   query kind=status 读同一快照，不做成两份状态）；
 * - 平台→游戏 / command：实现 {@link BusinessHooks}（协议层经 BusinessScheduler 派发进来，
 *   回调已在 Bukkit 主线程——onPlatformChat 全服广播，onCommand 阶段 3 填充）。
 */
public final class PaperRelay implements BusinessHooks {

    private final BindingStore bindings;
    private final ForwardRules rules;
    private final AdminTable admins;
    private final PaperCommandDispatcher commandDispatcher;
    private final KbLogger logger;
    private volatile PureWsServer server;
    private volatile StatusBody latestStatus;

    public PaperRelay(
            BindingStore bindings,
            ForwardRules rules,
            AdminTable admins,
            PaperCommandDispatcher commandDispatcher,
            KbLogger logger) {
        this.bindings = bindings;
        this.rules = rules;
        this.admins = admins;
        this.commandDispatcher = commandDispatcher;
        this.logger = logger;
    }

    /** PureWsServer 注入位（startAndWait 之后、监听器注册之前调用——无并发窗口）。 */
    public void attach(PureWsServer server) {
        this.server = server;
    }

    // ---- 游戏→平台事件 fan-out（监听器调用；Bukkit 主线程或异步聊天线程）----

    /** 聊天（AsyncChatEvent 异步线程调用；每绑定频道一帧 chat）。 */
    public void gameChat(String playerName, String content) {
        fanout(channel -> Frames.gameChat(new GameChatBody(channel, playerName, content)));
    }

    /** 进服（主线程；每绑定频道一帧 join）。 */
    public void playerJoin(String playerName) {
        fanout(channel -> Frames.join(new JoinBody(channel, playerName)));
    }

    /** 退服（主线程；每绑定频道一帧 leave）。 */
    public void playerLeave(String playerName) {
        fanout(channel -> Frames.leave(new LeaveBody(channel, playerName)));
    }

    /** 死亡（主线程；deathMessage 为 null 时上层已兜空串；每绑定频道一帧 death）。 */
    public void playerDeath(String player, String message) {
        fanout(channel -> Frames.death(new DeathBody(channel, player, message)));
    }

    /** status 单一入口：先落快照再广播（缓存与推送同源，镜像主仓 server.sendStatus）。 */
    public void pushStatus(StatusBody snapshot) {
        latestStatus = snapshot;
        PureWsServer current = server;
        if (current != null) {
            current.broadcast(Frames.status(snapshot));
        }
    }

    /** 绑定表变更 → bindings_updated（完整列表）广播（ConfigBindingStore 监听体）。 */
    public void bindingsUpdated() {
        PureWsServer current = server;
        if (current != null) {
            current.broadcast(Frames.bindingsUpdated(new BindingsUpdatedBody(bindings.boundChannels())));
        }
    }

    // ---- BusinessHooks：WS 入站 → 游戏（经 BusinessScheduler 派发，回调已在 Bukkit 主线程）----

    /**
     * 平台→游戏聊天：绑定频道过滤（未绑定 → debug 丢弃，镜像主仓 platformChatTarget 语义）
     * → 全服广播。渲染格式 = `<sender> content` 一行（镜像主仓 relay.forwardToGame 的组包
     * + NodeRequestHandler.onBroadcast 的 runTask→Bukkit.broadcast；本线回调已在主线程）。
     */
    @Override
    public void onPlatformChat(PlatformChatBody body) {
        if (!bindings.isBound(body.channel()) || !rules.shouldForwardToGame(body.channel())) {
            logger.debug(String.format("平台消息来自未绑定频道 %s，丢弃", body.channel()));
            return;
        }
        Bukkit.broadcast(Component.text("<" + body.sender() + "> " + body.content()));
    }

    /**
     * command 请求（管理员判定 + 执行全在本方法，对齐主仓 relay.handleCommandRequest 语义）：
     * 非管理员 → forbidden（warn 日志，不执行）；管理员 → 透传执行（无命令白名单——管理员即可
     * 执行任意命令，ADR-027）。回调已在 Bukkit 主线程（BukkitBusinessScheduler 派发），同步执行
     * 完命令组结果、以已完成 future 返回——whenComplete 的回帧也发生在主线程（线程安全出帧）。
     */
    @Override
    public CompletableFuture<CommandResultBody> onCommand(CommandBody body) {
        if (!admins.isAdmin(body.source().channel(), body.source().userId())) {
            logger.warn(String.format(
                    "非管理员来源执行命令被拒绝：channel=%s userId=%s command=%s",
                    body.source().channel(), body.source().userId(), body.command()));
            return CompletableFuture.completedFuture(CommandResultBody.failure("forbidden"));
        }
        return CompletableFuture.completedFuture(commandDispatcher.execute(body.command()));
    }

    @Override
    public Optional<StatusBody> latestStatus() {
        return Optional.ofNullable(latestStatus);
    }

    /** 逐绑定频道出帧（ForwardRules 二次策略位当前恒放行 = 主仓 forwarding.ts 现行语义）。 */
    private void fanout(Function<String, Frame> frameFor) {
        PureWsServer current = server;
        if (current == null) {
            return; // 监听器注册晚于 attach，理论不可达；防御性静默
        }
        for (String channel : bindings.boundChannels()) {
            if (rules.shouldForwardToPlatform(channel)) {
                current.broadcast(frameFor.apply(channel));
            }
        }
    }
}
