// Java-WebSocket 封装：子协议协商拒绝 + 文本帧入口 + close 封装 + 动态端口 listen(0)
package com.kurobridge.pure.core.server;

import com.kurobridge.pure.core.protocol.Frame;
import com.kurobridge.pure.core.protocol.FrameCodec;
import com.kurobridge.pure.core.protocol.ProtocolVersions;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeImpl1Server;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;

/**
 * kurobridge-ws 服务端（kurobridge 永远是 WS 服务端角色，对端主动连入）。
 *
 * 子协议协商：升级请求的 Sec-WebSocket-Protocol 列表包含 kurobridge-ws.v1 即接受并回显
 * （对齐主仓 ws 库 handleProtocols 语义）；缺失/不符在握手期抛 InvalidDataException(400)
 * 拒绝，不进协议层。host 为 null = 全部接口；port 0 = 动态端口（startAndWait 返回实际端口）。
 */
public final class PureWsServer extends WebSocketServer {

    private static final int HTTP_BAD_REQUEST = 400;

    private final SessionContext context;
    private final Map<WebSocket, PeerSession> sessions = new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private volatile int boundPort = -1;
    private volatile Exception bindFailure;

    public PureWsServer(String host, int port, SessionContext context) {
        super(
                new InetSocketAddress(host != null ? host : "0.0.0.0", port),
                List.of(new Draft_6455(List.of(), List.of(new Protocol(ProtocolVersions.WS_SUBPROTOCOL)))));
        this.context = context;
    }

    /**
     * 子协议门禁：缺失/不符 → HTTP 400 拒绝升级（不进协议层）。
     * 通过则返回基础 101 应答；Draft_6455.postProcessHandshakeResponseAsServer 负责回显
     * 选中子协议（1.6.0 起回调须返回 ServerHandshakeBuilder）。
     */
    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(
            WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
        String offered = request.getFieldValue("Sec-WebSocket-Protocol");
        boolean accepted = offered != null
                && Arrays.stream(offered.split(","))
                        .map(String::trim)
                        .anyMatch(ProtocolVersions.WS_SUBPROTOCOL::equals);
        if (!accepted) {
            throw new InvalidDataException(
                    HTTP_BAD_REQUEST, String.format("subprotocol required: %s", ProtocolVersions.WS_SUBPROTOCOL));
        }
        ServerHandshakeBuilder response = new HandshakeImpl1Server();
        response.setHttpStatus((short) 101);
        response.setHttpStatusMessage("Web Socket Protocol Handshake");
        return response;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        PeerSession session = new PeerSession(new JavaWebSocketConnection(conn), context);
        sessions.put(conn, session);
        session.start();
        context.logger().info(String.format("对端连入：%s", conn.getRemoteSocketAddress()));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        PeerSession session = sessions.get(conn);
        if (session != null) {
            session.onMessage(message);
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // 协议只用文本帧；二进制帧丢弃（warn 不断连，与非法帧同策略）
        context.logger().warn("收到二进制帧，丢弃（协议仅使用文本帧）");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        PeerSession session = sessions.remove(conn);
        if (session != null) {
            session.onClose();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null) {
            // 无连接上下文的错误 = 绑定期失败（Java-WebSocket run() 的 bind 异常走 handleFatal(null)
            // → onStart 永不回调）。startAndWait 轮询此标记即时失败，不让调用方空等超时。
            bindFailure = ex;
        }
        context.logger().error("WS 连接错误", ex);
    }

    @Override
    public void onStart() {
        boundPort = getPort();
        started.countDown();
        context.logger().info(String.format("WS 服务端已监听端口 %d", boundPort));
    }

    /**
     * 开始监听并等待绑定完成，返回实际端口（port=0 时为 OS 分配的动态端口）。
     * 端口被占用/地址不可用 → IllegalStateException 即时抛出（携带底层 BindException，
     * 不空等超时）；仍保留 10s 超时兜底 selector 线程无响应的极端情况。
     */
    public int startAndWait() throws InterruptedException {
        start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!started.await(200, TimeUnit.MILLISECONDS)) {
            Exception failure = bindFailure;
            if (failure != null) {
                throw new IllegalStateException(
                        String.format("WS 端口绑定失败（%s）：%s", getAddress(), failure.getMessage()), failure);
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("WS 服务端启动超时（10s 内未完成绑定）");
            }
        }
        return boundPort;
    }

    /** 主动关服：已握手对端 close 1001 "server shutdown"，随后停服务端。 */
    public void shutdown() throws InterruptedException {
        for (PeerSession session : sessions.values()) {
            session.shutdown();
        }
        sessions.clear();
        stop(1000);
    }

    /** 已握手对端数（观测用）。 */
    public int establishedCount() {
        int count = 0;
        for (PeerSession session : sessions.values()) {
            if (session.established()) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * 帧广播：发给全部已握手对端，返回送达数（对齐主仓 server.ts sendToEstablished 契约）。
     *
     * 可从任意线程调用（游戏侧事件 fan-out）；established/isOpen 两查与发送之间的关闭竞态按
     * 未送达计（catch 不外抛——事件推送是尽力而为语义）。
     */
    public int broadcast(Frame frame) {
        String text = FrameCodec.encode(frame);
        int delivered = 0;
        for (PeerSession session : sessions.values()) {
            try {
                if (session.send(text)) {
                    delivered += 1;
                }
            } catch (RuntimeException connectionDead) {
                // Java-WebSocket 对已断连接的 send 抛 WebsocketNotConnectedException：按未送达计
            }
        }
        if (delivered == 0) {
            context.logger().debug("无已握手对端，丢弃出帧");
        }
        return delivered;
    }

    /** WebSocket → WsConnection 适配（send 抛错视为连接已死，由 onClose 收尾）。 */
    private record JavaWebSocketConnection(WebSocket socket) implements WsConnection {

        @Override
        public void send(String text) {
            socket.send(text);
        }

        @Override
        public void close(int code, String reason) {
            socket.close(code, reason);
        }

        @Override
        public boolean isOpen() {
            return socket.isOpen();
        }
    }
}
