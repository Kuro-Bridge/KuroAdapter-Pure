// StatusCounts：quit 窗口在线数口径（剔除退出者 / 不重复剔除 / 单人与空列表边界）
package com.kurobridge.pure.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatusCountsTest {

    private static final UUID LEAVING = UUID.fromString("3f9d2c1e-8b4a-4c3e-9a2d-7f1e5b6c8d90");
    private static final UUID OTHER_1 = UUID.fromString("a1b2c3d4-1111-4222-8333-444455556666");
    private static final UUID OTHER_2 = UUID.fromString("c0ffee00-0000-4000-8000-000000000001");

    @Test
    void 三人在线含退出者_剔除后回2() {
        assertEquals(2, StatusCounts.countExcluding(List.of(OTHER_1, LEAVING, OTHER_2), LEAVING));
    }

    @Test
    void 退出者已不在列表_两人原样计数不重复剔除_回2() {
        assertEquals(2, StatusCounts.countExcluding(List.of(OTHER_1, OTHER_2), LEAVING));
    }

    @Test
    void 仅退出者一人_剔除后回0() {
        assertEquals(0, StatusCounts.countExcluding(List.of(LEAVING), LEAVING));
    }

    @Test
    void 空列表_回0() {
        assertEquals(0, StatusCounts.countExcluding(List.of(), LEAVING));
    }
}
