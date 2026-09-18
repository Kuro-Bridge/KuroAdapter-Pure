// 每连接握手状态机 + 两段式收帧分发 + 未知帧容忍（行为对齐主仓 bridge/core/src/server.ts）
package com.kurobridge.pure.core.server;

import com.kurobridge.pure.core.protocol.Frame;
import com.kurobridge.pure.core.protocol.FrameCodec;
import com.kurobridge.pure.core.protocol.FrameValidationException;
import com.kurobridge.pure.core.protocol.Frames;
import com.kurobridge.pure.core.protocol.InboundFrames;
import com.kurobridge.pure.core.protocol.ProtocolVersions;
import com.kurobridge.pure.core.protocol.VersionCompat;
import com.kurobridge.pure.core.protocol.WireFormatException;
import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.HelloBody;
import com.kurobridge.pure.core.protocol.message.PlatformChatBody;
import com.kurobridge.pure.core.protocol.message.QueryResultBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 单个对端连接的状态机：AWAITING_HELLO → ESTABLISHED（→ CLOSED）。
 *
 * WS 线程拥有除 {@link #state} 外的全部连接状态；{@code state} 为 volatile 供跨线程只读
 * （established/send 出口），协议应答（hello_ack/pong/query_result/未知帧回执）在 WS 线程
 * 内联完成，业务回调（chat/command）经 BusinessScheduler 派发。超时（hello 10s / 空闲 30s，
 * 0 = 禁用）经 TimeoutScheduler 注入。收帧永不断连：骨架失败/具体校验失败均 warn 丢弃。
 */
public final class PeerSession {

    /** WS 关闭码：协议错误（版本不匹配 / 握手超时）。 */
    public static final int CLOSE_PROTOCOL_ERROR = 1002;

    /** WS 关闭码：对端失联（空闲超时）/ 正常关服。 */
    public static final int CLOSE_GOING_AWAY = 1001;

    /** WS 关闭码：策略违规（鉴权失败）。 */
    public static final int CLOSE_POLICY_VIOLATION = 1008;

    public enum State {
        AWAITING_HELLO,
        ESTABLISHED,
        CLOSED,
    }

    private final WsConnection connection;
    private final SessionContext context;
    private final IdleTracker idleTracker;
    private TimeoutScheduler.Cancellable helloTimer;
    // volatile：游戏侧 fan-out（Bukkit 主线程/异步聊天线程）经 send()/established() 跨线程读；
    // 其余状态仍 WS 线程独占（见 docs/history/PAPER-WIRING-2026-09-18.md §1.1 逐字段分析）
    private volatile State state = State.AWAITING_HELLO;
    private String peerId;

    public PeerSession(WsConnection connection, SessionContext context) {
        this.connection = connection;
        this.context = context;
        this.idleTracker =
                new IdleTracker(context.timeouts().idleTimeoutMs(), context.scheduler(), this::onIdleTimeout);
    }

    /** 连接建立后武装 hello 超时与空闲计时（由持有方调用——构造器不外泄 this）。 */
    public void start() {
        armHelloTimeout();
        idleTracker.onActivity();
    }

    public State state() {
        return state;
    }

    public String peerId() {
        return peerId;
    }

    public boolean established() {
        return state == State.ESTABLISHED;
    }

    /** 文本帧入口（WS 线程调用）。任何入帧都重置空闲计时（含校验失败帧）。 */
    public void onMessage(String text) {
        idleTracker.onActivity();
        Frame frame;
        try {
            frame = FrameCodec.parseWire(text);
        } catch (WireFormatException invalid) {
            context.logger().warn(String.format("对端帧线格式校验失败，丢弃：%s", preview(text)));
            return;
        }
        String type = frame.type();
        if ("hello".equals(type)) {
            try {
                HelloBody body = InboundFrames.hello(frame);
                handleHello(frame.id(), body);
                return;
            } catch (FrameValidationException invalid) {
                // 非法 hello 不回执：连接保持到 hello 超时（协议契约）
            }
        } else if (!InboundFrames.KNOWN_TYPES.contains(type)) {
            handleUnknownFrame(frame);
            return;
        } else if (!established()) {
            context.logger().warn(String.format("握手完成前收到 %s，丢弃", type));
            return;
        } else if (handleEstablishedFrame(frame)) {
            return;
        }
        context.logger().warn(String.format("对端 %s 帧校验失败，丢弃：%s", type, preview(text)));
    }

    /** 连接关闭入口（WS 线程调用）：落定状态并清理定时器。 */
    public void onClose() {
        state = State.CLOSED;
        cancelHelloTimer();
        idleTracker.cancel();
        if (peerId != null) {
            context.logger().info(String.format("对端 %s 断开", peerId));
        } else {
            context.logger().debug("未握手连接关闭");
        }
    }

    /** 服务端主动关服：close 1001 "server shutdown"。 */
    public void shutdown() {
        connection.close(CLOSE_GOING_AWAY, "server shutdown");
    }

    /** 直发线文本（游戏侧事件 fan-out 用；返回是否送达已握手连接）。 */
    public boolean send(String text) {
        if (!established() || !connection.isOpen()) {
            return false;
        }
        connection.send(text);
        return true;
    }

    /** 便捷出帧（fan-out 用）。 */
    public boolean send(Frame frame) {
        return send(FrameCodec.encode(frame));
    }

    private void handleHello(String id, HelloBody body) {
        if (established()) {
            context.logger().warn(String.format("对端 %s 重复 hello，忽略", body.peerId()));
            return;
        }
        if (!VersionCompat.isCompatible(body.protocolVersion(), ProtocolVersions.PROTOCOL_VERSION)) {
            rejectHello(
                    id,
                    String.format("protocol version mismatch: peer=%s", body.protocolVersion()),
                    CLOSE_PROTOCOL_ERROR);
            return;
        }
        String token = context.token();
        if (!token.isEmpty() && !token.equals(body.token())) {
            rejectHello(id, "auth failed", CLOSE_POLICY_VIOLATION);
            return;
        }
        state = State.ESTABLISHED;
        peerId = body.peerId();
        cancelHelloTimer();
        connection.send(FrameCodec.encode(Frames.helloAckOk(
                id,
                context.serverId(),
                context.serverVersion(),
                List.copyOf(context.channelBindings().get()))));
        String clientSuffix = body.client() == null ? "" : String.format("，client=%s", body.client());
        context.logger().info(String.format("对端 %s 握手成功（platform=%s%s）", body.peerId(), body.platform(), clientSuffix));
    }

    private void rejectHello(String id, String reason, int closeCode) {
        cancelHelloTimer();
        idleTracker.cancel();
        connection.send(FrameCodec.encode(Frames.helloAckError(id, reason)));
        connection.close(closeCode, reason);
        context.logger().warn(String.format("握手被拒：%s", reason));
    }

    /** 已握手已知帧分发（ping/chat/command/query）；具体校验失败返回 false（调用方 warn 丢弃）。 */
    private boolean handleEstablishedFrame(Frame frame) {
        try {
            switch (frame.type()) {
                case "ping":
                    long timestamp = InboundFrames.ping(frame);
                    connection.send(FrameCodec.encode(Frames.pong(frame.id(), timestamp)));
                    return true;
                case "chat":
                    PlatformChatBody chat = InboundFrames.platformChat(frame);
                    context.businessScheduler().dispatch(() -> context.hooks().onPlatformChat(chat));
                    return true;
                case "command":
                    CommandBody command = InboundFrames.command(frame);
                    dispatchCommand(frame.id(), command);
                    return true;
                case "query":
                    String kind = InboundFrames.query(frame);
                    answerQuery(frame.id(), kind);
                    return true;
                default:
                    return false;
            }
        } catch (FrameValidationException invalid) {
            return false; // 已知 type 但具体校验失败 → warn 丢弃不回执不断连
        }
    }

    /**
     * 未知帧容忍（永不断连、无阈值计数；握手前同样生效）：带 id 且不以 _result 结尾 → 回
     * 同 id `<type>_result` {ok:false,"unknown frame type"}；_result 结尾 → 忽略（防乒乓）；
     * 无 id 事件 → 忽略 + debug。
     */
    private void handleUnknownFrame(Frame frame) {
        String type = frame.type();
        if (frame.id() == null) {
            context.logger().debug(String.format("收到未知事件帧 %s，忽略", type));
            return;
        }
        if (type.endsWith("_result")) {
            context.logger().debug(String.format("收到未知响应帧 %s，忽略（响应不回执，防乒乓循环）", type));
            return;
        }
        context.logger().debug(String.format("收到未知请求帧 %s，回执 unknown frame type", type));
        connection.send(FrameCodec.encode(Frames.unknownFrameResult(type, frame.id())));
    }

    /** command 请求：业务处理（管理员判定 + 执行）经 BusinessScheduler 派发，同 id 回执。 */
    private void dispatchCommand(String id, CommandBody body) {
        context.businessScheduler().dispatch(() -> invokeCommandHandler(id, body));
    }

    private void invokeCommandHandler(String id, CommandBody body) {
        CompletableFuture<CommandResultBody> future;
        try {
            future = context.hooks().onCommand(body);
        } catch (RuntimeException failure) {
            context.logger().error("command 处理抛出异常", failure);
            connection.send(
                    FrameCodec.encode(Frames.commandResult(id, CommandResultBody.failure(failure.getMessage()))));
            return;
        }
        if (future == null) {
            context.logger().warn("收到 command 帧但业务处理未注册，回执失败");
            connection.send(FrameCodec.encode(
                    Frames.commandResult(id, CommandResultBody.failure("command handler not available"))));
            return;
        }
        future.whenComplete((result, failure) -> {
            if (failure != null) {
                context.logger().error("command 执行失败", failure);
                String reason = failure.getMessage() != null
                        ? failure.getMessage()
                        : failure.getClass().getSimpleName();
                connection.send(FrameCodec.encode(Frames.commandResult(id, CommandResultBody.failure(reason))));
                return;
            }
            connection.send(FrameCodec.encode(Frames.commandResult(id, result)));
        });
    }

    /** query 请求：协议层本地作答（status 用最近快照；bindings 用绑定表实时快照）。 */
    private void answerQuery(String id, String kind) {
        switch (kind) {
            case "status" -> {
                Optional<StatusBody> snapshot = context.hooks().latestStatus();
                QueryResultBody result = snapshot.map(status -> QueryResultBody.success(Frames.statusNode(status)))
                        .orElseGet(() -> QueryResultBody.failure("no status yet"));
                connection.send(FrameCodec.encode(Frames.queryResult(id, result)));
            }
            case "bindings" ->
                connection.send(FrameCodec.encode(Frames.queryResult(
                        id,
                        QueryResultBody.success(
                                Frames.stringList(context.channelBindings().get())))));
            default -> context.logger().warn(String.format("未知 query kind：%s", kind));
        }
    }

    private void armHelloTimeout() {
        long timeoutMs = context.timeouts().helloTimeoutMs();
        if (timeoutMs <= 0) {
            return;
        }
        cancelHelloTimer();
        helloTimer = context.scheduler().schedule(timeoutMs, () -> {
            helloTimer = null;
            context.logger().warn(String.format("连接 %s 等待 hello 超时，关闭", describePeer()));
            connection.close(CLOSE_PROTOCOL_ERROR, "hello timeout");
        });
    }

    private void onIdleTimeout() {
        context.logger()
                .warn(String.format(
                        "对端 %s %dms 无任何帧，判定断开",
                        describePeer(), context.timeouts().idleTimeoutMs()));
        connection.close(CLOSE_GOING_AWAY, "idle timeout");
    }

    private void cancelHelloTimer() {
        if (helloTimer != null) {
            helloTimer.cancel();
            helloTimer = null;
        }
    }

    private String describePeer() {
        return peerId != null ? peerId : "（未握手）";
    }

    private static String preview(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
