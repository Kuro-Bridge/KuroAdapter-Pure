// PeerSession：握手状态机 + 两段式分发 + 未知帧容忍 + 超时（fake 连接 + 手动调度器）
package com.kurobridge.pure.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PeerSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HELLO_ID = "3f9d2c1e-8b4a-4c3e-9a2d-7f1e5b6c8d90";
    private static final String PING_ID = "a1b2c3d4-1111-4222-8333-444455556666";
    private static final String CMD_ID = "c0ffee00-0000-4000-8000-000000000001";
    private static final String QUERY_ID = "c0ffee00-0000-4000-8000-000000000003";

    // ---- 构造助手 ----

    private final Fakes.ManualScheduler scheduler = new Fakes.ManualScheduler();
    private final Fakes.RecordingHooks hooks = new Fakes.RecordingHooks();
    private final Fakes.CollectingLogger logger = new Fakes.CollectingLogger();

    private PeerSession newSession(Fakes.FakeConnection connection, String token) {
        PeerSession session = new PeerSession(
                connection,
                new SessionContext(
                        "srv-1",
                        "0.1.0",
                        token,
                        () -> List.of("114514", "1919810"),
                        ServerTimeouts.defaults(),
                        scheduler,
                        DirectBusinessScheduler.INSTANCE,
                        hooks,
                        logger));
        session.start();
        return session;
    }

    private PeerSession established(Fakes.FakeConnection connection) {
        PeerSession session = newSession(connection, "");
        session.onMessage(hello("0.4.0", "s3cret"));
        connection.clearSent();
        return session;
    }

    private static String hello(String protocolVersion, String token) {
        return "{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},\"body\":{\"peerId\":\"napuketto-01\","
                + "\"platform\":\"stub\",\"version\":\"1.0.0\",\"protocolVersion\":\"" + protocolVersion
                + "\",\"token\":\"" + token + "\",\"client\":\"stub/1.0\"}}";
    }

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    // ---- 握手 ----

    @Test
    void 合法hello_同id回hello_ack_ok_携带服务端身份与绑定快照() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "s3cret");

        session.onMessage(hello("0.4.0", "s3cret"));

        assertEquals(PeerSession.State.ESTABLISHED, session.state());
        assertEquals("napuketto-01", session.peerId());
        JsonNode ack = json(connection.lastSent());
        assertEquals("hello_ack", ack.path("header").path("type").asText());
        assertEquals(HELLO_ID, ack.path("header").path("id").asText());
        assertTrue(ack.path("body").path("ok").asBoolean());
        assertEquals("srv-1", ack.path("body").path("serverId").asText());
        assertEquals("0.1.0", ack.path("body").path("version").asText());
        assertEquals("0.4.0", ack.path("body").path("protocolVersion").asText());
        JsonNode bindings = ack.path("body").path("channelBindings");
        assertEquals(2, bindings.size());
        assertEquals("114514", bindings.get(0).asText());
        assertEquals("1919810", bindings.get(1).asText());
    }

    @Test
    void 旧版本对端_主版本相同即握手成功_ack回服务端实际版本() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage(hello("0.3.1", ""));

        assertEquals(PeerSession.State.ESTABLISHED, session.state());
        assertEquals(
                "0.4.0",
                json(connection.lastSent()).path("body").path("protocolVersion").asText());
    }

    @Test
    void 版本不匹配_hello_ack_error并close_1002_reason精确() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage(hello("9.9.9", ""));

        JsonNode ack = json(connection.lastSent());
        assertEquals("hello_ack", ack.path("header").path("type").asText());
        assertEquals(HELLO_ID, ack.path("header").path("id").asText());
        assertFalse(ack.path("body").path("ok").asBoolean());
        assertEquals(
                "protocol version mismatch: peer=9.9.9",
                ack.path("body").path("reason").asText());
        assertEquals(1002, connection.closedCode());
        assertEquals("protocol version mismatch: peer=9.9.9", connection.closedReason());
    }

    @Test
    void 版本协商先于鉴权_版本不匹配时即使token也错仍回1002() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "s3cret");

        session.onMessage(hello("9.9.9", "wrong"));

        assertEquals(1002, connection.closedCode());
    }

    @Test
    void token缺失_服务端token非空_hello_ack_error并close_1008() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "s3cret");

        session.onMessage("{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},\"body\":{\"peerId\":\"p\","
                + "\"platform\":\"stub\",\"version\":\"0.0.1\",\"protocolVersion\":\"0.4.0\"}}");

        JsonNode ack = json(connection.lastSent());
        assertFalse(ack.path("body").path("ok").asBoolean());
        assertEquals("auth failed", ack.path("body").path("reason").asText());
        assertEquals(1008, connection.closedCode());
        assertEquals("auth failed", connection.closedReason());
    }

    @Test
    void token不符_同样1008_auth_failed() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "s3cret");

        session.onMessage(hello("0.4.0", "wrong"));

        assertEquals(1008, connection.closedCode());
        assertEquals("auth failed", connection.closedReason());
    }

    @Test
    void 服务端token空_不鉴权_缺token握手成功() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage("{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},\"body\":{\"peerId\":\"p\","
                + "\"platform\":\"stub\",\"version\":\"0.0.1\",\"protocolVersion\":\"0.4.0\"}}");

        assertEquals(PeerSession.State.ESTABLISHED, session.state());
        assertFalse(connection.wasClosed());
    }

    @Test
    void 非法hello_不回执_等待超时close_1002_hello_timeout() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage("{\"header\":{\"type\":\"hello\",\"id\":\"" + HELLO_ID + "\"},\"body\":{\"peerId\":\"p\","
                + "\"platform\":\"stub\",\"version\":\"0.0.1\",\"protocolVersion\":\"abc\"}}");

        assertTrue(connection.sent().isEmpty());

        scheduler.advance(10_000);

        assertEquals(1002, connection.closedCode());
        assertEquals("hello timeout", connection.closedReason());
    }

    @Test
    void hello超时_10秒内未握手close_1002() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");
        assertFalse(connection.wasClosed());

        scheduler.advance(10_000);

        assertEquals(1002, connection.closedCode());
        assertEquals("hello timeout", connection.closedReason());
    }

    @Test
    void 重复hello_忽略并warn() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(hello("0.4.0", ""));

        assertTrue(connection.sent().isEmpty());
        assertTrue(logger.warns().stream().anyMatch(line -> line.contains("重复 hello")));
        assertEquals(PeerSession.State.ESTABLISHED, session.state());
    }

    @Test
    void 握手前已知业务帧_丢弃不回执() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage("{\"header\":{\"type\":\"ping\",\"id\":\"" + PING_ID + "\"},\"body\":{\"timestamp\":1}}");
        session.onMessage(
                "{\"header\":{\"type\":\"chat\"},\"body\":{\"channel\":\"114514\",\"sender\":\"A\",\"content\":\"hi\"}}");

        assertTrue(connection.sent().isEmpty());
        assertFalse(connection.wasClosed());
    }

    // ---- 心跳与空闲 ----

    @Test
    void ping_同id回pong_timestamp原样回显() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"ping\",\"id\":\"" + PING_ID + "\"},\"body\":{\"timestamp\":1760000000000}}");

        JsonNode pong = json(connection.lastSent());
        assertEquals("pong", pong.path("header").path("type").asText());
        assertEquals(PING_ID, pong.path("header").path("id").asText());
        assertEquals(1760000000000L, pong.path("body").path("timestamp").asLong());
    }

    @Test
    void ping校验失败_timestamp负数_丢弃不回执不断连() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage("{\"header\":{\"type\":\"ping\",\"id\":\"" + PING_ID + "\"},\"body\":{\"timestamp\":-1}}");

        assertTrue(connection.sent().isEmpty());
        assertFalse(connection.wasClosed());
    }

    @Test
    void 空闲超时_30秒无入帧close_1001_idle_timeout() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        established(connection);

        scheduler.advance(30_000);

        assertEquals(1001, connection.closedCode());
        assertEquals("idle timeout", connection.closedReason());
    }

    @Test
    void 空闲计时_任何入帧重置_ping续命() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        scheduler.advance(29_999);
        session.onMessage("{\"header\":{\"type\":\"ping\",\"id\":\"" + PING_ID + "\"},\"body\":{\"timestamp\":1}}");
        scheduler.advance(29_999);
        assertFalse(connection.wasClosed());

        scheduler.advance(1);

        assertEquals(1001, connection.closedCode());
        assertEquals("idle timeout", connection.closedReason());
    }

    @Test
    void 空闲计时_非法帧也重置() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        scheduler.advance(29_999);
        session.onMessage("not-json");
        scheduler.advance(29_999);
        assertFalse(connection.wasClosed());

        scheduler.advance(1);

        assertEquals(1001, connection.closedCode());
    }

    // ---- 未知帧容忍（永不断连）----

    @Test
    void 未知请求帧_回同id的unknown_result() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage("{\"header\":{\"type\":\"stub_unknown\",\"id\":\"" + PING_ID + "\"},\"body\":{\"any\":1}}");

        JsonNode reply = json(connection.lastSent());
        assertEquals("stub_unknown_result", reply.path("header").path("type").asText());
        assertEquals(PING_ID, reply.path("header").path("id").asText());
        assertFalse(reply.path("body").path("ok").asBoolean());
        assertEquals("unknown frame type", reply.path("body").path("error").asText());
        assertFalse(connection.wasClosed());
    }

    @Test
    void 未知事件帧_忽略不回执() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage("{\"header\":{\"type\":\"stub_unknown_event\"},\"body\":{}}");

        assertTrue(connection.sent().isEmpty());
        assertFalse(connection.wasClosed());
    }

    @Test
    void 未知_result帧_不回执防乒乓() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"mystery_result\",\"id\":\"" + PING_ID + "\"},\"body\":{\"ok\":true}}");

        assertTrue(connection.sent().isEmpty());
        assertFalse(connection.wasClosed());
    }

    @Test
    void 未知帧容忍_握手前同样生效() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        session.onMessage("{\"header\":{\"type\":\"stub_unknown\",\"id\":\"" + PING_ID + "\"},\"body\":{\"any\":1}}");

        assertEquals(
                "stub_unknown_result",
                json(connection.lastSent()).path("header").path("type").asText());
    }

    @Test
    void 骨架失败帧_丢弃不断连_未知type大写被骨架拒绝() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage("{\"header\":{\"type\":\"Hello\"},\"body\":{}}");

        assertTrue(connection.sent().isEmpty());
        assertFalse(connection.wasClosed());
    }

    // ---- 业务帧 ----

    @Test
    void 平台chat_握手后回调业务hooks() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"chat\"},\"body\":{\"channel\":\"114514\",\"sender\":\"群友A\",\"content\":\"你好 MC\"}}");

        assertEquals(1, hooks.chats().size());
        assertEquals("114514", hooks.chats().get(0).channel());
        assertEquals("群友A", hooks.chats().get(0).sender());
        assertEquals("你好 MC", hooks.chats().get(0).content());
        assertTrue(connection.sent().isEmpty());
    }

    @Test
    void 已知type校验失败_chat空content_丢弃不回执不断连() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"chat\"},\"body\":{\"channel\":\"114514\",\"sender\":\"群友A\",\"content\":\"\"}}");

        assertTrue(connection.sent().isEmpty());
        assertTrue(hooks.chats().isEmpty());
        assertFalse(connection.wasClosed());
    }

    @Test
    void command_业务未注册_回command_handler_not_available() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"command\",\"id\":\"" + CMD_ID + "\"},\"body\":{\"command\":\"whitelist list\","
                        + "\"source\":{\"channel\":\"114514\",\"userId\":\"10001\"}}}");

        JsonNode result = json(connection.lastSent());
        assertEquals("command_result", result.path("header").path("type").asText());
        assertEquals(CMD_ID, result.path("header").path("id").asText());
        assertFalse(result.path("body").path("ok").asBoolean());
        assertEquals(
                "command handler not available",
                result.path("body").path("error").asText());
    }

    @Test
    void command_业务已注册_同id回ok_output() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);
        hooks.setCommandHandler(body -> CompletableFuture.completedFuture(
                CommandResultBody.success(List.of("There are 1 whitelisted player(s): Steve"))));

        session.onMessage(
                "{\"header\":{\"type\":\"command\",\"id\":\"" + CMD_ID + "\"},\"body\":{\"command\":\"whitelist list\","
                        + "\"source\":{\"channel\":\"114514\",\"userId\":\"10001\"}}}");

        JsonNode result = json(connection.lastSent());
        assertEquals(CMD_ID, result.path("header").path("id").asText());
        assertTrue(result.path("body").path("ok").asBoolean());
        assertEquals(
                "There are 1 whitelisted player(s): Steve",
                result.path("body").path("output").get(0).asText());
        assertEquals(1, hooks.commands().size());
        CommandBody seen = hooks.commands().get(0);
        assertEquals("whitelist list", seen.command());
        assertEquals("114514", seen.source().channel());
        assertEquals("10001", seen.source().userId());
    }

    @Test
    void command_前导斜杠_校验拒绝丢弃() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage("{\"header\":{\"type\":\"command\",\"id\":\"" + CMD_ID
                + "\"},\"body\":{\"command\":\"/whitelist list\","
                + "\"source\":{\"channel\":\"114514\",\"userId\":\"10001\"}}}");

        assertTrue(connection.sent().isEmpty());
        assertTrue(hooks.commands().isEmpty());
    }

    @Test
    void command_业务future异常_同id回error() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);
        hooks.setCommandHandler(body -> CompletableFuture.failedFuture(new IllegalStateException("boom")));

        session.onMessage(
                "{\"header\":{\"type\":\"command\",\"id\":\"" + CMD_ID + "\"},\"body\":{\"command\":\"say hi\","
                        + "\"source\":{\"channel\":\"114514\",\"userId\":\"10001\"}}}");

        JsonNode result = json(connection.lastSent());
        assertFalse(result.path("body").path("ok").asBoolean());
        assertEquals("boom", result.path("body").path("error").asText());
    }

    @Test
    void query_bindings_回实时绑定快照() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"query\",\"id\":\"" + QUERY_ID + "\"},\"body\":{\"kind\":\"bindings\"}}");

        JsonNode result = json(connection.lastSent());
        assertEquals("query_result", result.path("header").path("type").asText());
        assertEquals(QUERY_ID, result.path("header").path("id").asText());
        assertTrue(result.path("body").path("ok").asBoolean());
        assertEquals("114514", result.path("body").path("data").get(0).asText());
        assertEquals("1919810", result.path("body").path("data").get(1).asText());
    }

    @Test
    void query_status_无缓存回no_status_yet() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"query\",\"id\":\"" + QUERY_ID + "\"},\"body\":{\"kind\":\"status\"}}");

        JsonNode result = json(connection.lastSent());
        assertFalse(result.path("body").path("ok").asBoolean());
        assertEquals("no status yet", result.path("body").path("error").asText());
    }

    @Test
    void query_status_有缓存回快照data() throws Exception {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);
        hooks.setStatusSupplier(() -> Optional.of(new StatusBody(20.0, 2, 3600)));

        session.onMessage(
                "{\"header\":{\"type\":\"query\",\"id\":\"" + QUERY_ID + "\"},\"body\":{\"kind\":\"status\"}}");

        JsonNode result = json(connection.lastSent());
        assertTrue(result.path("body").path("ok").asBoolean());
        assertEquals(20, result.path("body").path("data").path("tps").asDouble(), 0.0001);
        assertEquals(2, result.path("body").path("data").path("onlinePlayers").asLong());
        assertEquals(
                3600, result.path("body").path("data").path("uptimeSeconds").asLong());
    }

    @Test
    void query校验失败_未知kind_丢弃不回执() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onMessage(
                "{\"header\":{\"type\":\"query\",\"id\":\"" + QUERY_ID + "\"},\"body\":{\"kind\":\"mystery\"}}");

        assertTrue(connection.sent().isEmpty());
    }

    // ---- 生命周期 ----

    @Test
    void onClose_落定CLOSED并清理定时器() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.onClose();

        assertEquals(PeerSession.State.CLOSED, session.state());
        assertEquals(0, scheduler.pendingCount());
    }

    @Test
    void shutdown_主动关服close_1001_server_shutdown() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = established(connection);

        session.shutdown();

        assertEquals(1001, connection.closedCode());
        assertEquals("server shutdown", connection.closedReason());
    }

    @Test
    void send_未握手或已关闭_不出帧() {
        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        PeerSession session = newSession(connection, "");

        assertFalse(session.send("{\"header\":{\"type\":\"status\"},\"body\":{}}"));
        assertTrue(connection.sent().isEmpty());
    }
}
