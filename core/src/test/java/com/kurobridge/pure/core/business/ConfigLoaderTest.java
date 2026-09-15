// ConfigLoader：配置解析矩阵（对齐主仓 config.test.ts，剔除 runtime/embedded 段后）
package com.kurobridge.pure.core.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void 合法配置通过并去重保序() {
        PureConfig config = ConfigLoader.parse("{\"channels\":[\"10001\",\"10002\",\"10001\"],\"admins\":["
                + "{\"channel\":\"10001\",\"users\":[\"alice\",\"bob\",\"alice\"]},"
                + "{\"channel\":\"10002\",\"users\":[\"carol\"]},"
                + "{\"channel\":\"10001\",\"users\":[\"dave\"]}]}");
        assertEquals(List.of("10001", "10002"), config.channels());
        assertEquals("", config.token());
        assertEquals(2, config.admins().size());
        assertEquals("10001", config.admins().get(0).channel());
        assertEquals(List.of("alice", "bob"), config.admins().get(0).users());
        assertEquals(List.of("carol"), config.admins().get(1).users());
        assertNull(config.ws());
    }

    @Test
    void token与admins缺省补齐_向后兼容旧配置() {
        PureConfig config = ConfigLoader.parse("{\"channels\":[\"10001\"]}");
        assertEquals(List.of("10001"), config.channels());
        assertEquals("", config.token());
        assertEquals(List.of(), config.admins());
        assertNull(config.ws());

        PureConfig withToken = ConfigLoader.parse("{\"channels\":[],\"token\":\"s3cret\"}");
        assertEquals("s3cret", withToken.token());
    }

    @Test
    void channels缺失_非数组_含空串_根非对象_均抛ConfigException() {
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":\"10001\"}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[\"\"]}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("null"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("\"nope\""));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("[\"10001\"]"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("not-json"));
    }

    @Test
    void token非字符串_admins形状非法_抛ConfigException() {
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"token\":123}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"admins\":\"alice\"}"));
        assertThrows(
                ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"admins\":[{\"channel\":\"\"}]}"));
        assertThrows(
                ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"admins\":[{\"channel\":\"c\"}]}"));
        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.parse("{\"channels\":[],\"admins\":[{\"channel\":\"c\",\"users\":[\"\"]}]}"));
        assertThrows(
                ConfigException.class,
                () -> ConfigLoader.parse("{\"channels\":[],\"admins\":[{\"channel\":\"c\",\"users\":\"alice\"}]}"));
    }

    @Test
    void 多余字段剥离_服主加注释性字段不炸() {
        PureConfig config = ConfigLoader.parse("{\"channels\":[\"10001\"],\"extra\":1,\"token\":\"t\"}");
        assertEquals(List.of("10001"), config.channels());
        assertEquals("t", config.token());
    }

    @Test
    void ws段_整段缺省_完整_只配host_只配port() {
        assertNull(ConfigLoader.parse("{\"channels\":[]}").ws());

        PureConfig.WsListen full = ConfigLoader.parse(
                        "{\"channels\":[],\"ws\":{\"host\":\"127.0.0.1\",\"port\":25580}}")
                .ws();
        assertEquals("127.0.0.1", full.host());
        assertEquals(25580, full.port());

        PureConfig.WsListen hostOnly = ConfigLoader.parse("{\"channels\":[],\"ws\":{\"host\":\"127.0.0.1\"}}")
                .ws();
        assertEquals("127.0.0.1", hostOnly.host());
        assertNull(hostOnly.port());

        PureConfig.WsListen portOnly =
                ConfigLoader.parse("{\"channels\":[],\"ws\":{\"port\":25580}}").ws();
        assertNull(portOnly.host());
        assertEquals(25580, portOnly.port());
    }

    @Test
    void ws段非法值_抛ConfigException() {
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":{\"port\":0}}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":{\"port\":65536}}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":{\"port\":25580.5}}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":{\"port\":\"25580\"}}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":{\"host\":\"\"}}"));
        assertThrows(ConfigException.class, () -> ConfigLoader.parse("{\"channels\":[],\"ws\":\"25580\"}"));
    }

    @Test
    void 默认配置_空绑定_不鉴权_无管理员_无ws段() {
        PureConfig config = PureConfig.defaults();
        assertEquals(List.of(), config.channels());
        assertEquals("", config.token());
        assertEquals(List.of(), config.admins());
        assertNull(config.ws());
    }

    @Test
    void 从文件加载_读写失败抛ConfigException(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.json");
        Files.writeString(file, "{\"channels\":[\"10001\"],\"token\":\"s3cret\"}");
        PureConfig config = ConfigLoader.load(file);
        assertEquals(List.of("10001"), config.channels());
        assertEquals("s3cret", config.token());

        assertTrue(Files.exists(file));
        assertThrows(ConfigException.class, () -> ConfigLoader.load(tempDir.resolve("missing.json")));
    }
}
