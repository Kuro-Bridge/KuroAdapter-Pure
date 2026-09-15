// 版本协商：主版本兼容区间矩阵
package com.kurobridge.pure.core.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionCompatTest {

    private static final String SERVER = "0.4.0";

    @Test
    void 主版本相同即兼容_次补丁自由浮动() {
        assertTrue(VersionCompat.isCompatible("0.2.0", SERVER));
        assertTrue(VersionCompat.isCompatible("0.3.1", SERVER));
        assertTrue(VersionCompat.isCompatible("0.4.0", SERVER));
        assertTrue(VersionCompat.isCompatible("0.4.99", SERVER));
    }

    @Test
    void 主版本不同不兼容() {
        assertFalse(VersionCompat.isCompatible("1.0.0", SERVER));
        assertFalse(VersionCompat.isCompatible("9.9.9", SERVER));
        assertFalse(VersionCompat.isCompatible(SERVER, "1.0.0"));
    }

    @Test
    void 任一侧格式非法即不兼容() {
        assertFalse(VersionCompat.isCompatible("abc", SERVER));
        assertFalse(VersionCompat.isCompatible("1.2", SERVER));
        assertFalse(VersionCompat.isCompatible("0.4", SERVER));
        assertFalse(VersionCompat.isCompatible("1.2.3.4", SERVER));
        assertFalse(VersionCompat.isCompatible("", SERVER));
        assertFalse(VersionCompat.isCompatible(SERVER, "not-semver"));
        assertFalse(VersionCompat.isCompatible("v0.4.0", SERVER));
    }
}
