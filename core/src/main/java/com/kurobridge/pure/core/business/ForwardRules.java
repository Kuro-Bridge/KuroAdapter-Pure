// 转发规则：平台↔游戏双向的转发判定（下一阶段按配置/策略实现）
package com.kurobridge.pure.core.business;

/**
 * 转发判定接口（空实现一律放行，过滤交给 BindingStore 的绑定表）。
 */
public interface ForwardRules {

    /** 平台→游戏方向是否放行（channel 已命中绑定表的前提下的二次策略位）。 */
    boolean shouldForwardToGame(String channel);

    /** 游戏→平台方向是否放行（事件按频道 fan-out 的前提下的二次策略位）。 */
    boolean shouldForwardToPlatform(String channel);

    /** 空实现：一律放行（下一阶段：转发规则配置）。 */
    ForwardRules EMPTY = new ForwardRules() {
        @Override
        public boolean shouldForwardToGame(String channel) {
            return true;
        }

        @Override
        public boolean shouldForwardToPlatform(String channel) {
            return true;
        }
    };
}
