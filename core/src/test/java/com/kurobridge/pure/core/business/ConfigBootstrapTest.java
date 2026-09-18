// ConfigBootstrap：首启生成（D1/D3/D7）与 token 门禁（D2）矩阵（@TempDir 纯文件逻辑）
package com.kurobridge.pure.core.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigBootstrapTest {

    @Test
    void 缺失时生成安全默认且可被ConfigLoader解析(@TempDir Path tempDir) {
        Path file = tempDir.resolve("config.json");

        assertTrue(ConfigBootstrap.ensureConfigFile(file), "文件缺失应生成");
        PureConfig config = ConfigLoader.load(file); // round-trip：生成件即文档，经单一权威解析继续启用
        assertEquals(List.of(), config.channels());
        assertFalse(config.token().isEmpty(), "生成件带随机非空 token（安全默认，非裸奔）");
        assertEquals(List.of(), config.admins());
        assertEquals(PureConfig.DEFAULT_SERVER_ID, config.serverId());
        assertEquals(ConfigBootstrap.DEFAULT_WS_HOST, config.ws().host());
        assertEquals(ConfigBootstrap.DEFAULT_WS_PORT, config.ws().port());

        assertFalse(ConfigBootstrap.ensureConfigFile(file), "既有文件不重复生成");
    }

    @Test
    void token为32位hex且两次生成互不相同(@TempDir Path tempDirA, @TempDir Path tempDirB) {
        ConfigBootstrap.ensureConfigFile(tempDirA.resolve("config.json"));
        ConfigBootstrap.ensureConfigFile(tempDirB.resolve("config.json"));

        String tokenA = ConfigLoader.load(tempDirA.resolve("config.json")).token();
        String tokenB = ConfigLoader.load(tempDirB.resolve("config.json")).token();
        assertTrue(tokenA.matches("[0-9a-f]{32}"), "token 应为 32 位小写 hex（16 字节 SecureRandom）");
        assertTrue(tokenB.matches("[0-9a-f]{32}"));
        assertFalse(tokenA.equals(tokenB), "两次生成应互不相同");
    }

    @Test
    void 既有文件字节不动(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.json");
        String existing = "{\"channels\":[\"10001\"],\"token\":\"s3cret\",\"comment\":\"服主手写\"}";
        Files.writeString(file, existing);

        assertFalse(ConfigBootstrap.ensureConfigFile(file));

        assertEquals(existing, Files.readString(file), "生成不得覆盖既有配置（CREATE_NEW 语义）");
    }

    @Test
    void 父目录一并创建(@TempDir Path tempDir) {
        Path file = tempDir.resolve("KuroBridgePure").resolve("nested").resolve("config.json");

        assertTrue(ConfigBootstrap.ensureConfigFile(file));

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void 目录创建失败抛ConfigException指路(@TempDir Path tempDir) throws IOException {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "同名的普通文件阻挡目录创建");
        Path file = blocker.resolve("config.json");

        ConfigException failure = assertThrows(ConfigException.class, () -> ConfigBootstrap.ensureConfigFile(file));
        assertTrue(failure.getMessage().contains("blocker"), "报错须指路被阻挡的路径：" + failure.getMessage());
    }

    // ---- requireToken（D2：空 token 拒绝监听，指路文案）----

    @Test
    void requireToken_空token抛指路_非空放行(@TempDir Path tempDir) {
        Path file = tempDir.resolve("config.json");
        PureConfig empty = ConfigLoader.parse("{\"channels\":[]}"); // 缺失 token → ""

        ConfigException failure = assertThrows(ConfigException.class, () -> ConfigBootstrap.requireToken(empty, file));
        assertTrue(failure.getMessage().contains(file.toString()), "文案须含文件路径");
        assertTrue(failure.getMessage().contains("非空"), "文案须指路「设非空令牌」");
        assertTrue(failure.getMessage().contains("对端"), "文案须提醒对端同 token");

        PureConfig configured = ConfigLoader.parse("{\"channels\":[],\"token\":\"dev\"}"); // 本机调试：任意非空串
        ConfigBootstrap.requireToken(configured, file); // 不抛
    }
}
