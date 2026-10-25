// Component → 纯文本的跨 Adventure 版本兼容链（对齐主仓 PlainText，ADR-038）
package com.kurobridge.pure.paper;

import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Component → 纯文本的跨 Adventure 版本兼容链（聊天 / 命令输出收集共用）。
 *
 * <p>序列化器在 Adventure 线上换代过一次：{@code PlainTextComponentSerializer}（4.15+，
 * Paper 1.20.6+ 捆绑；5.x 唯一存留）取代 {@code PlainComponentSerializer}（4.x pre-4.15，
 * 5.x 已删除）。静态初始化互不拖累的隔离持有者 + 首用解析缓存，任一版本缺失只会烧掉
 * 自己的 holder，不会把整类标记失败（主仓 Paper 26.3 上 PlainComponentSerializer 被删
 * 导致聊天/死亡消息全崩的事故即由此而来）。
 *
 * <p>解析顺序：现代（对 1.21.4 paper-api 编译，1.20.6+ 服务器命中）→ 旧（{@link LegacyPlain}
 * 隔离持有者，覆盖 1.19 ~ 1.21.x 的 adventure 4.x）。两者都不可用即抛 IllegalStateException——
 * 1.16.5 之前的无 Adventure 服务器不会走到本类（聊天走 LegacyChatListener 的 String 路径）。
 */
final class PlainText {

    private static volatile Function<Component, String> serializer;

    private PlainText() {}

    static String serialize(Component component) {
        Function<Component, String> current = serializer;
        if (current == null) {
            synchronized (PlainText.class) {
                current = serializer;
                if (current == null) {
                    current = resolve();
                    serializer = current;
                }
            }
        }
        return current.apply(component);
    }

    private static Function<Component, String> resolve() {
        try {
            // 触碰 holder 即触发其静态初始化；Adventure 5.x 之外的服务器在此烧掉 ModernHolder
            PlainTextComponentSerializer modern = ModernHolder.SERIALIZER;
            return modern::serialize;
        } catch (Throwable modernUnavailable) {
            // 落入旧序列化器（LegacyPlain 隔离持有者）；probe 调用强制解析方法体里引用的
            // PlainComponentSerializer——adventure 5.x 服务器在此抛 NoClassDefFoundError
            try {
                LegacyPlain.serialize(Component.empty());
                return LegacyPlain::serialize;
            } catch (Throwable legacyUnavailable) {
                throw new IllegalStateException("无可用 Component plain 序列化器（Adventure 4/5 均缺失）", legacyUnavailable);
            }
        }
    }

    /** 现代 holder：独立类，静态初始化失败只烧掉自己。 */
    private static final class ModernHolder {
        static final PlainTextComponentSerializer SERIALIZER = PlainTextComponentSerializer.plainText();
    }
}
