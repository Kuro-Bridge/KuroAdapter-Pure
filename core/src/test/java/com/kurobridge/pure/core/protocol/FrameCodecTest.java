// FrameCodec：线格式编解码 + 两段式解析的骨架/分发行为
package com.kurobridge.pure.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kurobridge.pure.core.protocol.message.JoinBody;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrameCodecTest {

    private static final String UUID = "3f9d2c1e-8b4a-4c3e-9a2d-7f1e5b6c8d90";

    @Test
    void 请求帧往返_编码后骨架解析还原type_id_body() {
        Frame pong = Frames.pong(UUID, 1760000000000L);
        Frame parsed = FrameCodec.parseWire(FrameCodec.encode(pong));
        assertEquals("pong", parsed.type());
        assertEquals(UUID, parsed.id());
        assertEquals(pong.body(), parsed.body());
    }

    @Test
    void 事件帧往返_id为null() {
        Frame join = Frames.join(new JoinBody("114514", "Alex"));
        Frame parsed = FrameCodec.parseWire(FrameCodec.encode(join));
        assertEquals("join", parsed.type());
        assertNull(parsed.id());
        assertEquals(join.body(), parsed.body());
    }

    @Test
    void 骨架拒绝_非法JSON() {
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("not-json"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire(""));
    }

    @Test
    void 骨架拒绝_根非对象() {
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("[]"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("null"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("\"nope\""));
    }

    @Test
    void 骨架拒绝_缺header或header非对象() {
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("{\"body\":{}}"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("{\"header\":\"x\",\"body\":{}}"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("{\"header\":[],\"body\":{}}"));
    }

    @Test
    void 骨架拒绝_type大写_缺type_或带非法字符() {
        assertThrows(
                WireFormatException.class, () -> FrameCodec.parseWire("{\"header\":{\"type\":\"Hello\"},\"body\":{}}"));
        assertThrows(WireFormatException.class, () -> FrameCodec.parseWire("{\"header\":{},\"body\":{}}"));
        assertThrows(
                WireFormatException.class,
                () -> FrameCodec.parseWire("{\"header\":{\"type\":\"chat-x\"},\"body\":{}}"));
        assertThrows(
                WireFormatException.class, () -> FrameCodec.parseWire("{\"header\":{\"type\":\"1chat\"},\"body\":{}}"));
    }

    @Test
    void 骨架拒绝_id非UUID() {
        assertThrows(
                WireFormatException.class,
                () -> FrameCodec.parseWire("{\"header\":{\"type\":\"chat\",\"id\":\"not-a-uuid\"},\"body\":{}}"));
        // UUID.fromString 接受无横线串，骨架必须按 canonical 8-4-4-4-12 拒绝
        assertThrows(
                WireFormatException.class,
                () -> FrameCodec.parseWire(
                        "{\"header\":{\"type\":\"chat\",\"id\":\"123e4567e89b12d3a456426614174000\"},\"body\":{}}"));
        assertThrows(
                WireFormatException.class,
                () -> FrameCodec.parseWire("{\"header\":{\"type\":\"chat\",\"id\":123},\"body\":{}}"));
    }

    @Test
    void 骨架通过_id缺省或canonical_UUID大小写() {
        Frame noId = FrameCodec.parseWire("{\"header\":{\"type\":\"chat\"},\"body\":{}}");
        assertNull(noId.id());
        Frame upper = FrameCodec.parseWire(
                "{\"header\":{\"type\":\"chat\",\"id\":\"3F9D2C1E-8B4A-4C3E-9A2D-7F1E5B6C8D90\"},\"body\":{}}");
        assertEquals("3F9D2C1E-8B4A-4C3E-9A2D-7F1E5B6C8D90", upper.id());
    }

    @Test
    void 事件帧携带id_分发段拒绝() {
        Frame frame = FrameCodec.parseWire("{\"header\":{\"type\":\"chat\",\"id\":\"" + UUID + "\"},"
                + "\"body\":{\"channel\":\"114514\",\"sender\":\"群友A\",\"content\":\"大家好\"}}");
        assertThrows(FrameValidationException.class, () -> InboundFrames.platformChat(frame));
    }

    @Test
    void 请求帧缺id_分发段拒绝() {
        Frame frame = FrameCodec.parseWire(
                "{\"header\":{\"type\":\"command\"},"
                        + "\"body\":{\"command\":\"whitelist list\",\"source\":{\"channel\":\"114514\",\"userId\":\"10001\"}}}");
        assertThrows(FrameValidationException.class, () -> InboundFrames.command(frame));
        Frame ping = FrameCodec.parseWire("{\"header\":{\"type\":\"ping\"},\"body\":{\"timestamp\":1}}");
        assertThrows(FrameValidationException.class, () -> InboundFrames.ping(ping));
    }

    @Test
    void body未知字段剥离_不报错() {
        Frame hello = FrameCodec.parseWire("{\"header\":{\"type\":\"hello\",\"id\":\"" + UUID + "\"},"
                + "\"body\":{\"peerId\":\"p1\",\"platform\":\"stub\",\"version\":\"1.0.0\","
                + "\"protocolVersion\":\"0.4.0\",\"token\":\"t\",\"client\":\"c\",\"extra\":\"ignored\"}}");
        var body = InboundFrames.hello(hello);
        assertEquals("p1", body.peerId());
        assertEquals("stub", body.platform());
        assertEquals("1.0.0", body.version());
        assertEquals("0.4.0", body.protocolVersion());
        assertEquals("t", body.token());
        assertEquals("c", body.client());
    }

    @Test
    void 请求帧header多余键_骨架与分发段均容忍() {
        Frame hello = FrameCodec.parseWire(
                "{\"header\":{\"type\":\"hello\",\"id\":\"" + UUID + "\",\"trace\":\"x\"},"
                        + "\"body\":{\"peerId\":\"p1\",\"platform\":\"stub\",\"version\":\"1.0.0\",\"protocolVersion\":\"0.4.0\"}}");
        assertEquals("p1", InboundFrames.hello(hello).peerId());
    }

    @Test
    void 出帧集合编码均为单行JSON() {
        Frame ack = Frames.helloAckOk(UUID, "srv-1", "0.1.0", List.of("114514"));
        String line = FrameCodec.encode(ack);
        assertEquals(-1, line.indexOf('\n'));
        Frame parsed = FrameCodec.parseWire(line);
        OutboundFrames.validate(parsed);
        assertEquals("hello_ack", parsed.type());
    }
}
