// 频道绑定集合：fan-out 迭代 + 入站过滤 + 变更通知位（bindings_updated 推送用）
package com.kurobridge.pure.core.business;

import java.util.List;

/**
 * 绑定表接口（SSOT 在服主配置 channels；下一阶段接配置热重载真实现）。
 */
public interface BindingStore {

    /** 当前绑定频道（fan-out 迭代用；顺序稳定）。 */
    List<String> boundChannels();

    /** 频道是否绑定（平台→游戏入站过滤用）。 */
    boolean isBound(String channel);

    /** 注册绑定表变更监听（bindings_updated 推送位；下一阶段接线）。 */
    void addChangeListener(Runnable listener);

    /** 空实现（下一阶段：配置驱动的真实现 + 热重载推送）。 */
    BindingStore EMPTY = new BindingStore() {
        @Override
        public List<String> boundChannels() {
            return List.of();
        }

        @Override
        public boolean isBound(String channel) {
            return false;
        }

        @Override
        public void addChangeListener(Runnable listener) {
            // 下一阶段：绑定集合变化 → 通知监听 → bindings_updated 推送
        }
    };
}
