// 白名单网关的 Paper 实现：经同一命令执行机制组 whitelist add|remove|list（ADR-027：SSOT=原生）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.business.WhitelistGateway;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 白名单网关（ADR-027：SSOT = MC 原生 whitelist.json，本仓不自建存储）：whitelist 子命令
 * 经 {@link PaperCommandDispatcher} 以 CONSOLE 名义执行并收集输出，与 onCommand 的透传路径
 * 复用同一执行机制。本阶段无调用方（command 帧直传 `whitelist ...` 字符串即达同效），
 * 供结构化调用未来用（如面板/上层 API）。
 *
 * <p>线程契约：方法须在 Bukkit 主线程调用（执行机制的主线程前提）；结果以已完成 future
 * 返回（同步执行完才组结果）。
 */
public final class PaperWhitelistGateway implements WhitelistGateway {

    private final PaperCommandDispatcher dispatcher;

    public PaperWhitelistGateway(PaperCommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public CompletableFuture<List<String>> list() {
        CommandResultBody result = dispatcher.execute("whitelist list");
        if (!result.ok()) {
            return CompletableFuture.failedFuture(new IllegalStateException(result.error()));
        }
        return CompletableFuture.completedFuture(result.output() == null ? List.of() : result.output());
    }

    @Override
    public CompletableFuture<Void> add(String player) {
        return voidResult(dispatcher.execute("whitelist add " + player));
    }

    @Override
    public CompletableFuture<Void> remove(String player) {
        return voidResult(dispatcher.execute("whitelist remove " + player));
    }

    private static CompletableFuture<Void> voidResult(CommandResultBody result) {
        if (!result.ok()) {
            return CompletableFuture.failedFuture(new IllegalStateException(result.error()));
        }
        return CompletableFuture.completedFuture(null);
    }
}
