// 白名单网关：语义上走 MC 原生 whitelist 命令（SSOT = whitelist.json，本仓不自建存储）
package com.kurobridge.pure.core.business;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 白名单网关接口（command 帧里 whitelist add/remove/list 的语义出口）。
 * 下一阶段 Paper 侧经 Bukkit dispatchCommand 以 CONSOLE 名义执行并收集输出。
 */
public interface WhitelistGateway {

    /** whitelist list：输出行集合。 */
    CompletableFuture<List<String>> list();

    /** whitelist add <player>。 */
    CompletableFuture<Void> add(String player);

    /** whitelist remove <player>。 */
    CompletableFuture<Void> remove(String player);

    /** 空实现：未接线（下一阶段接 Bukkit 命令派发 + 输出收集）。 */
    WhitelistGateway EMPTY = new WhitelistGateway() {
        @Override
        public CompletableFuture<List<String>> list() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("whitelist gateway 未接线（下一阶段）"));
        }

        @Override
        public CompletableFuture<Void> add(String player) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("whitelist gateway 未接线（下一阶段）"));
        }

        @Override
        public CompletableFuture<Void> remove(String player) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("whitelist gateway 未接线（下一阶段）"));
        }
    };
}
