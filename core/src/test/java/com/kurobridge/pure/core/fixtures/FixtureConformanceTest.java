// KuroProtocol 金样本门禁：两仓一致性——schema 语义逐帧消费 + 握手行为断言（PIN 见 fixtures/PIN.md）
package com.kurobridge.pure.core.fixtures;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kurobridge.pure.core.protocol.Frame;
import com.kurobridge.pure.core.protocol.FrameCodec;
import com.kurobridge.pure.core.protocol.FrameValidationException;
import com.kurobridge.pure.core.protocol.InboundFrames;
import com.kurobridge.pure.core.protocol.OutboundFrames;
import com.kurobridge.pure.core.protocol.WireFormatException;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.server.DirectBusinessScheduler;
import com.kurobridge.pure.core.server.Fakes;
import com.kurobridge.pure.core.server.PeerSession;
import com.kurobridge.pure.core.server.ServerTimeouts;
import com.kurobridge.pure.core.server.SessionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 金样本一致性门禁（夹具语义见 KuroProtocol docs/fixtures.md）：
 * schema=accept → 每帧按 dir 方向 wire 骨架 + 具体帧型校验必须通过；
 * schema=reject+stage=wire → wire 骨架必须拒绝；reject+stage=dispatch → 骨架过、方向校验必拒；
 * behavior（reply/close 非空时）→ 用 PeerSession + fake 连接跑首帧，断言回执帧语义等价与
 * close code/reason 精确一致。
 */
class FixtureConformanceTest {

    /** pin 守卫：v0.4 夹具固定 16 份（KuroProtocol 增补夹具时须重新 pin 并更新此数）。 */
    private static final int EXPECTED_FIXTURE_COUNT = 16;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> consumeAllFixtures() throws Exception {
        Path root = Path.of(
                FixtureConformanceTest.class.getResource("/fixtures/v0.4").toURI());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertEquals(EXPECTED_FIXTURE_COUNT, files.size(), "pin 守卫：v0.4 应有 16 份夹具");
        return files.stream()
                .map(file -> DynamicTest.dynamicTest(root.relativize(file).toString(), () -> runFixture(file)));
    }

    private void runFixture(Path file) throws Exception {
        JsonNode fixture = MAPPER.readTree(file.toFile());
        String schema = fixture.path("expect").path("schema").asText();
        String stage = fixture.path("expect").path("reject").path("stage").asText(null);

        for (JsonNode entry : fixture.path("frames")) {
            String dir = entry.path("dir").asText();
            String wire = MAPPER.writeValueAsString(entry.path("frame"));
            if ("accept".equals(schema)) {
                Frame parsed = assertDoesNotThrow(() -> FrameCodec.parseWire(wire), dir + " wire 骨架应通过");
                assertDoesNotThrow(() -> validateByDirection(dir, parsed), dir + " 具体帧型校验应通过");
            } else if ("reject".equals(schema) && "wire".equals(stage)) {
                assertThrows(WireFormatException.class, () -> FrameCodec.parseWire(wire), dir + " wire 骨架应拒绝");
            } else if ("reject".equals(schema)) {
                Frame parsed = assertDoesNotThrow(() -> FrameCodec.parseWire(wire), dir + " wire 骨架应通过");
                assertThrows(
                        FrameValidationException.class,
                        () -> validateByDirection(dir, parsed),
                        dir + " 具体帧型校验应拒绝（dispatch 段）");
            } else {
                throw new IllegalStateException("未知 expect.schema：" + schema);
            }
        }

        runBehaviorIfObservable(fixture);
    }

    private void validateByDirection(String dir, Frame frame) {
        if ("peer->server".equals(dir)) {
            InboundFrames.validate(frame);
        } else if ("server->peer".equals(dir)) {
            OutboundFrames.validate(frame);
        } else {
            throw new IllegalStateException("未知 dir：" + dir);
        }
    }

    /** behavior 契约（reply/close 任一非空才可观测）：跑 PeerSession 断言回执与关闭行为。 */
    private void runBehaviorIfObservable(JsonNode fixture) throws Exception {
        JsonNode behavior = fixture.path("expect").path("behavior");
        JsonNode replyRef = behavior.path("reply");
        JsonNode closeExpect = behavior.path("close");
        if (replyRef.isNull() && closeExpect.isNull()) {
            return; // 无可观测行为（纯事件帧/拒绝样本已在 schema 段覆盖）
        }
        JsonNode firstFrame = fixture.path("frames").get(0);
        assertEquals("peer->server", firstFrame.path("dir").asText(), "行为断言样本的首帧应为对端→服务端方向");

        Fakes.FakeConnection connection = new Fakes.FakeConnection();
        Fakes.RecordingHooks hooks = new Fakes.RecordingHooks();
        // command-roundtrip 样本的期望回执 = 管理员命令执行成功 + 一行输出
        hooks.setCommandHandler(body -> CompletableFuture.completedFuture(
                CommandResultBody.success(List.of("There are 1 whitelisted player(s): Steve"))));
        PeerSession session = new PeerSession(
                connection,
                new SessionContext(
                        "srv-1",
                        "0.1.0",
                        "s3cret",
                        () -> List.of("114514", "1919810"),
                        ServerTimeouts.defaults(),
                        new Fakes.ManualScheduler(),
                        DirectBusinessScheduler.INSTANCE,
                        hooks,
                        new Fakes.CollectingLogger()));
        session.start();
        // 业务帧样本（ping/command/query）假定会话已建立：先完成内部握手（回执不参与断言）
        if (!"hello".equals(firstFrame.path("frame").path("header").path("type").asText())) {
            session.onMessage("{\"header\":{\"type\":\"hello\",\"id\":\"11111111-1111-4111-8111-111111111111\"},"
                    + "\"body\":{\"peerId\":\"pin-hello\",\"platform\":\"stub\",\"version\":\"1.0.0\","
                    + "\"protocolVersion\":\"0.4.0\",\"token\":\"s3cret\"}}");
            assertTrue(session.established(), "内部握手应成功");
            connection.clearSent();
        }
        session.onMessage(MAPPER.writeValueAsString(firstFrame.path("frame")));

        if (!replyRef.isNull()) {
            int index = Integer.parseInt(replyRef.asText().replaceAll("\\D+", ""));
            JsonNode expectedReply = fixture.path("frames").get(index).path("frame");
            JsonNode actualReply = MAPPER.readTree(connection.lastSent());
            assertEquals(expectedReply, actualReply, "回执帧应与金样本语义等价");
        } else {
            assertTrue(connection.sent().isEmpty(), "不应有任何回执");
        }
        if (closeExpect.isObject()) {
            assertEquals(closeExpect.path("code").asInt(), connection.closedCode(), "close code 应精确一致");
            assertEquals(closeExpect.path("reason").asText(), connection.closedReason(), "close reason 应精确一致");
        } else {
            assertFalse(connection.wasClosed(), "不应关闭连接");
        }
    }
}
