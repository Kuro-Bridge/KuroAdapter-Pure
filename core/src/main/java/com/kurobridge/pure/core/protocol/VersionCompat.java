// 版本协商（主版本兼容区间）：主版本号相同即兼容；任一侧格式非法 = 不兼容
package com.kurobridge.pure.core.protocol;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 协议版本兼容判定（对齐 KuroProtocol src/meta.ts 的 isProtocolVersionCompatible）。 */
public final class VersionCompat {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private VersionCompat() {}

    /** peer 与 server 的主版本号相同即兼容（次/补丁位自由浮动）；任一侧不满足三元组格式 = 不兼容。 */
    public static boolean isCompatible(String peerVersion, String serverVersion) {
        Optional<Integer> peer = majorOf(peerVersion);
        Optional<Integer> server = majorOf(serverVersion);
        return peer.isPresent() && peer.equals(server);
    }

    private static Optional<Integer> majorOf(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1), 10));
        } catch (NumberFormatException overflow) {
            // 主版本号超 int 上限：按不兼容处理（防御性，正常对端不会发）
            return Optional.empty();
        }
    }
}
