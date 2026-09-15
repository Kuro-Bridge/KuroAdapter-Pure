// PureWsServer 轻量真机自测：动态端口 + 子协议接受/拒绝 + 回环握手（Java-WebSocket 客户端）
package com.kurobridge.pure.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kurobridge.pure.core.protocol.ProtocolVersions;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PureWsServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HELLO_ID = "3f9d2c1e-8b4a-4c3e-9a2d-7f1e5b6c8d90";

    private PureWsServer server;
    private TestClient client;

    /** 回环客户端：携带子协议；记录首帧消息与服务端 close。 */
    private static final class TestClient extends WebSocketClient {

        final CompletableFuture<String> firstMessage = new CompletableFuture<>();
        final CompletableFuture<String> closed = new CompletableFuture<>();
        volatile int closeCode;
        volatile String closeReason;

        TestClient(URI uri, boolean withSubprotocol) {
            super(
                    uri,
                    withSubprotocol
                            ? new Draft_6455(List.of(), List.of(new Protocol(ProtocolVersions.WS_SUBPROTOCOL)))
                            : new Draft_6455());
        }

        @Override
        public void onOpen(ServerHandshake handshake) {}

        @Override
        public void onMessage(String message) {
            firstMessage.complete(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closed.complete(reason);
        }

        @Override
        public void onError(Exception ex) {}
    }

    private PureWsServer startServer() throws InterruptedException {
        server = new PureWsServer(
                "127.0.0.1",
                0,
                new SessionContext(
                        "srv-1",
                        "0.1.0",
                        "",
                        () -> List.of("114514"),
                        ServerTimeouts.defaults(),
                        new Fakes.ManualScheduler(),
                        DirectBusinessScheduler.INSTANCE,
                        new Fakes.RecordingHooks(),
                        new Fakes.CollectingLogger()));
        int port = server.startAndWait();
        assertTrue(port > 0, "动态端口 listen(0) 应返回实际端口");
        return server;
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (client != null && !client.isClosed()) {
            client.close();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void 子协议正确_回环握手成功_动态端口() throws Exception {
        startServer();
        client = new TestClient(new URI("ws://127.0.0.1:" + server.getPort() + "/"), true);
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS), "携带子协议的连接应被接受");

        client.send("{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},"
                + "\"body\":{\"peerId\":\"napuketto-01\",\"platform\":\"stub\",\"version\":\"1.0.0\","
                + "\"protocolVersion\":\"0.4.0\"}}");

        JsonNode ack = MAPPER.readTree(client.firstMessage.get(5, TimeUnit.SECONDS));
        assertEquals("hello_ack", ack.path("header").path("type").asText());
        assertEquals(HELLO_ID, ack.path("header").path("id").asText());
        assertTrue(ack.path("body").path("ok").asBoolean());
        assertEquals("0.4.0", ack.path("body").path("protocolVersion").asText());
        assertEquals(1, server.establishedCount());
    }

    @Test
    void 子协议缺失_握手期被拒_HTTP400不进协议层() throws Exception {
        startServer();
        client = new TestClient(new URI("ws://127.0.0.1:" + server.getPort() + "/"), false);

        boolean connected = client.connectBlocking(5, TimeUnit.SECONDS);

        assertFalse(connected, "缺失子协议的升级应被拒绝");
        assertFalse(client.isOpen());
        assertEquals(0, server.establishedCount());
    }

    @Test
    void 主动关服_已握手对端收到close_1001_server_shutdown() throws Exception {
        startServer();
        client = new TestClient(new URI("ws://127.0.0.1:" + server.getPort() + "/"), true);
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS));
        client.send("{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},"
                + "\"body\":{\"peerId\":\"p\",\"platform\":\"stub\",\"version\":\"1.0.0\","
                + "\"protocolVersion\":\"0.4.0\"}}");
        client.firstMessage.get(5, TimeUnit.SECONDS);
        assertEquals(1, server.establishedCount());

        server.shutdown();

        client.closed.get(5, TimeUnit.SECONDS);
        assertEquals(1001, client.closeCode);
        assertEquals("server shutdown", client.closeReason);
    }
}
