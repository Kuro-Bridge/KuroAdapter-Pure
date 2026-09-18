// ConfigReloadReport：D6 热/冷矩阵的机械对比（回执分组的唯一事实来源）
package com.kurobridge.pure.core.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigReloadReportTest {

    private static PureConfig parse(String json) {
        return ConfigLoader.parse(json);
    }

    @Test
    void 无变化_两组均为空() {
        ConfigReloadReport report = ConfigReloadReport.diff(
                parse("{\"channels\":[\"c\"],\"token\":\"t\",\"server\":{\"id\":\"s\"}}"),
                parse("{\"channels\":[\"c\"],\"token\":\"t\",\"server\":{\"id\":\"s\"}}"));

        assertTrue(report.hotApplied().isEmpty());
        assertTrue(report.restartPending().isEmpty());
    }

    @Test
    void channels与admins变化_进已热更组() {
        ConfigReloadReport report = ConfigReloadReport.diff(
                parse("{\"channels\":[\"a\"],\"admins\":[{\"channel\":\"a\",\"users\":[\"u1\"]}]}"),
                parse("{\"channels\":[\"b\"],\"admins\":[{\"channel\":\"b\",\"users\":[\"u2\"]}]}"));

        assertEquals(2, report.hotApplied().size());
        assertEquals("绑定频道（bindings_updated 已广播）", report.hotApplied().get(0));
        assertEquals("管理员表", report.hotApplied().get(1));
        assertTrue(report.restartPending().isEmpty(), "channels/admins 均为热字段");
    }

    @Test
    void token与serverId变化_进待重启组() {
        ConfigReloadReport report = ConfigReloadReport.diff(
                parse("{\"channels\":[],\"token\":\"old\",\"server\":{\"id\":\"old\"}}"),
                parse("{\"channels\":[],\"token\":\"new\",\"server\":{\"id\":\"new\"}}"));

        assertTrue(report.hotApplied().isEmpty());
        assertEquals(2, report.restartPending().size());
        assertTrue(report.restartPending().contains("token"));
        assertTrue(report.restartPending().contains("server.id"));
    }

    @Test
    void ws段缺省到显式_host与port均报待重启() {
        ConfigReloadReport report = ConfigReloadReport.diff(
                parse("{\"channels\":[]}"), parse("{\"channels\":[],\"ws\":{\"host\":\"127.0.0.1\",\"port\":25580}}"));

        assertEquals(List.of("ws.host", "ws.port"), report.restartPending());
    }
}
