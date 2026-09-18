// BindingStore 配置驱动实现：PureConfig.channels 支撑 + 整表替换通知位（热重载用）
package com.kurobridge.pure.core.business;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 绑定表真实现（SSOT = 服主配置 channels；本阶段构造注入，变更来源是未来的热重载）。
 *
 * 线程：channels 为 volatile（替换可能在主线程外触发）；监听 CopyOnWriteArrayList，
 * 通知在替换线程上同步执行（监听方自行保证轻量——只做出帧广播）。
 */
public final class ConfigBindingStore implements BindingStore {

    private volatile List<String> channels;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public ConfigBindingStore(List<String> channels) {
        this.channels = List.copyOf(channels);
    }

    @Override
    public List<String> boundChannels() {
        return channels;
    }

    @Override
    public boolean isBound(String channel) {
        return channels.contains(channel);
    }

    @Override
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * 整表替换：集合变化时通知全部监听并返回 true（对齐主仓 BindingTable.replace 的
     * 「变化才推」语义）；相同集合替换 = no-op 返回 false。
     */
    public boolean replace(List<String> next) {
        List<String> candidate = List.copyOf(next);
        if (candidate.equals(channels)) {
            return false;
        }
        channels = candidate;
        for (Runnable listener : listeners) {
            listener.run();
        }
        return true;
    }
}
