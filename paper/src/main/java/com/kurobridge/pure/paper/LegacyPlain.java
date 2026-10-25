package com.kurobridge.pure.paper;

import net.kyori.adventure.text.Component;

// 注意：PlainComponentSerializer 用全限定名引用——import 行的弃用警告不受
// 类级 @SuppressWarnings 覆盖，会触发 -Werror（本类存在的目的就是用它，见类注释）。

/**
 * 旧 Adventure（4.x pre-4.15，Paper 1.19 ~ 1.21.x 捆绑）的 Component → 纯文本序列化器
 * （对齐主仓 :paper-legacy 的 LegacyPlain，ADR-038）。
 *
 * <p>{@code PlainComponentSerializer} 自 adventure 4.15 弃用、5.x（Paper 26.x）删除。
 * Pure 对表 1.21.4 的 adventure 4.x：新旧两个序列化器类都还在编译面上，故不需要主仓的
 * 独立 :paper-legacy 模块——本类即旧序列化器的隔离持有者，静态初始化失败只烧掉自己。
 * 运行期只允许经 {@link PlainText} 兼容链在现代序列化器不可用时触碰——在 adventure 5.x
 * 服务器上加载本类会抛 {@code NoClassDefFoundError}，由调用方捕获。
 */
@SuppressWarnings("deprecation") // 刻意使用被 1.21.4 API 弃用的旧序列化器（见类注释）
final class LegacyPlain {

    private static final net.kyori.adventure.text.serializer.plain.PlainComponentSerializer SERIALIZER =
            net.kyori.adventure.text.serializer.plain.PlainComponentSerializer.plain();

    private LegacyPlain() {}

    static String serialize(Component component) {
        return SERIALIZER.serialize(component);
    }
}
