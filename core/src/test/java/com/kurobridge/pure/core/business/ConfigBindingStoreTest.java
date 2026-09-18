// ConfigBindingStore：绑定快照 + isBound + 整表替换通知（变化才通知，相同集合 no-op）
package com.kurobridge.pure.core.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigBindingStoreTest {

    @Test
    void 构造注入的频道即初始绑定表_顺序稳定() {
        ConfigBindingStore store = new ConfigBindingStore(List.of("114514", "1919810"));

        assertEquals(List.of("114514", "1919810"), store.boundChannels());
        assertTrue(store.isBound("114514"));
        assertFalse(store.isBound("999"));
    }

    @Test
    void 替换为不同集合_通知监听并返回true() {
        ConfigBindingStore store = new ConfigBindingStore(List.of("a"));
        List<String> notified = new ArrayList<>();
        store.addChangeListener(() -> notified.add(String.join(",", store.boundChannels())));

        assertTrue(store.replace(List.of("b", "c")));

        assertEquals(List.of("b,c"), notified, "集合变化应通知一次，监听内读到新表");
        assertEquals(List.of("b", "c"), store.boundChannels());
        assertTrue(store.isBound("c"));
        assertFalse(store.isBound("a"));
    }

    @Test
    void 替换为相同集合_noop返回false不通知() {
        ConfigBindingStore store = new ConfigBindingStore(List.of("a", "b"));
        List<String> notified = new ArrayList<>();
        store.addChangeListener(() -> notified.add("fired"));

        assertFalse(store.replace(List.of("a", "b")));

        assertTrue(notified.isEmpty(), "相同集合替换不应通知（对齐主仓「变化才推」）");
    }

    @Test
    void 替换为空集_等于清空绑定() {
        ConfigBindingStore store = new ConfigBindingStore(List.of("a"));
        List<String> notified = new ArrayList<>();
        store.addChangeListener(() -> notified.add("fired"));

        assertTrue(store.replace(List.of()));

        assertTrue(store.boundChannels().isEmpty());
        assertFalse(store.isBound("a"));
        assertEquals(1, notified.size());
    }

    @Test
    void boundChannels返回不可变视图_外部修改不影响内部() {
        ConfigBindingStore store = new ConfigBindingStore(List.of("a"));

        List<String> snapshot = store.boundChannels();
        assertTrue(snapshot.contains("a"));

        store.replace(List.of("b"));
        assertEquals(List.of("b"), store.boundChannels(), "快照每次读当前表");
    }
}
