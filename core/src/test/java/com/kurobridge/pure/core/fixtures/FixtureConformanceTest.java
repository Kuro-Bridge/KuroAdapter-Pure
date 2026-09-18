// KuroProtocol 金样本门禁：两仓一致性——SHA256SUMS 内容级 pin + schema 语义逐帧消费 + 握手行为断言（PIN 见 fixtures/PIN.md）
package com.kurobridge.pure.core.fixtures;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kurobridge.pure.core.protocol.Frame;
import com.kurobridge.pure.core.protocol.FrameCodec;
import com.kurobridge.pure.core.protocol.FrameValidationException;
import com.kurobridge.pure.core.protocol.InboundFrames;
import com.kurobridge.pure.core.protocol.OutboundFrames;
import com.kurobridge.pure.core.protocol.ProtocolVersions;
import com.kurobridge.pure.core.protocol.WireFormatException;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.server.DirectBusinessScheduler;
import com.kurobridge.pure.core.server.Fakes;
import com.kurobridge.pure.core.server.PeerSession;
import com.kurobridge.pure.core.server.ServerTimeouts;
import com.kurobridge.pure.core.server.SessionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * 金样本一致性门禁（夹具语义见 KuroProtocol docs/fixtures.md）：
 * 内容级 pin——SHA256SUMS 逐文件哈希 + 清单/目录集合一致 + 版本↔目录推导（本类三件套）；
 * schema=accept → 每帧按 dir 方向 wire 骨架 + 具体帧型校验必须通过；
 * schema=reject+stage=wire → wire 骨架必须拒绝；reject+stage=dispatch → 骨架过、方向校验必拒；
 * behavior（reply/close 非空时）→ 用 PeerSession + fake 连接跑首帧，断言回执帧语义等价与
 * close code/reason 精确一致。
 */
class FixtureConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 内容级 pin 守卫一：解析 SHA256SUMS（SSOT 原样拷贝件），逐条校验文件存在 + SHA-256 一致
     * （Java 标准库 MessageDigest），且清单集合 == 目录下实际 *.json 集合（多出/缺失都报错）。
     * 旧"固定 16 份"份数守卫由此升级：夹具内容或份数任一漂移都在这里炸。
     */
    @Test
    void pinManifestMatchesContentAndFileSet() throws Exception {
        Path root = fixturesRoot();
        Map<String, String> manifest = new LinkedHashMap<>();
        for (String line : Files.readAllLines(root.resolve("SHA256SUMS"))) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            assertEquals(2, parts.length, "SHA256SUMS 行格式应为 `<sha256>  <相对路径>`：" + line);
            manifest.put(parts[1].trim(), parts[0].toLowerCase());
        }
        assertFalse(manifest.isEmpty(), "SHA256SUMS 清单不应为空");

        Set<String> actual = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> actual.add(root.relativize(path).toString().replace('\\', '/')));
        }
        assertEquals(new TreeSet<>(manifest.keySet()), actual, "SHA256SUMS 清单集合应与目录下实际 *.json 集合一致");

        for (Map.Entry<String, String> entry : manifest.entrySet()) {
            Path file = root.resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file), "清单文件应存在：" + entry.getKey());
            assertEquals(entry.getValue(), sha256Of(file), "SHA-256 不一致（内容被改动或拷贝失真）：" + entry.getKey());
        }
    }

    @TestFactory
    Stream<DynamicTest> consumeAllFixtures() throws Exception {
        Path root = fixturesRoot();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        return files.stream()
                .map(file -> DynamicTest.dynamicTest(root.relativize(file).toString(), () -> runFixture(file)));
    }

    /**
     * 内容级 pin 守卫二：夹具目录名由 {@link ProtocolVersions#PROTOCOL_VERSION} 推导
     * （"0.4.0" → 去掉 patch 段 "0.4" → "v0.4"），目录必须存在且就是本测试消费的目录——
     * 协议常量与夹具版本漂移时在此处炸，而非静默消费旧目录。
     */
    private static Path fixturesRoot() throws Exception {
        String version = ProtocolVersions.PROTOCOL_VERSION;
        String dir = "v" + version.substring(0, version.lastIndexOf('.'));
        var resource = FixtureConformanceTest.class.getResource("/fixtures/" + dir);
        assertNotNull(
                resource, "由 PROTOCOL_VERSION=" + version + " 推导的夹具目录 /fixtures/" + dir + " 应存在（协议 bump 须重拷目录+SUMS）");
        return Path.of(resource.toURI());
    }

    private static String sha256Of(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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
