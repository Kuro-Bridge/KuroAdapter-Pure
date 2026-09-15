// 协议元信息常量——注意：本文件是【人工同步的硬编码副本】，SSOT 在姊妹仓 KuroProtocol
// 的 src/meta.ts（协议 bump 时须同批更新本副本并重新 pin fixtures）
package com.kurobridge.pure.core.protocol;

/** kurobridge-ws 协议常量（v0.4.0：品牌迁移 kurobot-ws → kurobridge-ws，帧形状零变化）。 */
public final class ProtocolVersions {

    /** 语义化协议版本（hello 期按主版本兼容区间协商）。 */
    public static final String PROTOCOL_VERSION = "0.4.0";

    /** WS 子协议（大版本；升级握手缺失/不符即拒绝，不进协议层）。 */
    public static final String WS_SUBPROTOCOL = "kurobridge-ws.v1";

    private ProtocolVersions() {}
}
