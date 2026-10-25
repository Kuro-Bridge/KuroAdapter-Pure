// 首启配置生成（D1/D3/D7）+ token 必配门禁（D2）：纯文件/纯函数逻辑，Bukkit 壳保持薄
package com.kurobridge.pure.core.business;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;

/**
 * 配置引导（纯逻辑，无 Bukkit API；生成件即文档）：
 *
 * <ul>
 *   <li>{@link #resolveConfigFile(Path)}：配置路径解析（ADR-040）——<b>config.jsonc 优先</b>；
 *       缺失但存在旧版 config.json 时回退读取（JSONC 解析器是 JSON 超集，不迁移不重命名）；
 *       两者皆缺返回 config.jsonc（供首启生成）。
 *   <li>{@link #ensureConfigFile(Path)}：文件缺失时生成带注释的 JSONC 模板落盘（D7 形状：channels []、
 *       token 32 位 hex 随机、admins []、server.id 默认、ws 127.0.0.1:25580）。注释文案对齐主仓
 *       {@code defaultConfigJsonc}（ADR-040）。写入用 CREATE_NEW 语义防覆盖（等价主仓 wx）：并发竞争
 *       落到既有文件时静默退回，随后照常走 ConfigLoader 加载（单一权威，不为生成件开第二解析路径）。
 *       生成仅发生在启用期；运行期 reload 的文件缺失不在此处补写（调用方直接 load 并报错）。
 *   <li>{@link #requireToken(PureConfig, Path)}：token 空（缺失或 ""）→ ConfigException 指路后
 *       由调用方中止启用（D2 安全默认，对主仓「WARN 不阻断」的有意偏差）。
 * </ul>
 */
public final class ConfigBootstrap {

    /** 配置文件（JSONC，ADR-040）：首启生成注释模板，注释即文档。 */
    public static final String CONFIG_FILE_NAME = "config.jsonc";

    /** 旧版配置文件：仅回退读取，不迁移不重命名。 */
    public static final String LEGACY_CONFIG_FILE_NAME = "config.json";

    /** 生成件的 ws.host（环回 = 安全默认，也是对端静态 url 的开箱即用值）。 */
    public static final String DEFAULT_WS_HOST = "127.0.0.1";

    /** 生成件的 ws.port（与 koishi-dev 部署值及 SMOKE 登记一致的 25580）。 */
    public static final int DEFAULT_WS_PORT = 25580;

    /** D3：随机 token = 16 字节 SecureRandom → 32 位 hex。 */
    private static final int TOKEN_BYTES = 16;

    /** JSONC 模板（逐行；token/server/ws 值经占位符注入，避免与常量双份字面量）。 */
    private static final String[] TEMPLATE_LINES = {
        "// KuroAdapter-Pure 配置文件（JSONC：支持行注释、块注释与行尾逗号；VS Code 原生识别 .jsonc）",
        "// 字段权威参考：docs/modules/core-config.md（与代码不一致时以代码为准）",
        "// 可热更字段只有 channels 与 admins（/kurobridge reload），其余字段改后需重启。",
        "{",
        "    // 绑定频道列表（必填，可为空数组）：群聊消息只有命中表内频道才进游戏，",
        "    // 游戏事件按此表逐频道 fan-out。重复项只保留首个。",
        "    // 例：\"channels\": [\"123456789\", \"987654321\"]",
        "    \"channels\": [],",
        "",
        "    // WS 握手鉴权 token：首启已自动生成随机值，对端连入时必须携带同一令牌。",
        "    // 清空（\"\"）= 不鉴权，但本线启动门禁拒绝空 token（安全默认，对齐主仓 ADR-040）。改后需重启。",
        "    \"token\": \"${token}\",",
        "",
        "    // 群管理员映射（缺省 [] = 无人可经 command 执行命令）：",
        "    // 频道命中且用户 ID 在 users 内才放行。运行期可热载。",
        "    // 例：\"admins\": [{ \"channel\": \"123456789\", \"users\": [\"10001\"] }]",
        "    \"admins\": [],",
        "",
        "    // 服务器标识：经 hello_ack.serverId 上报给对端，缺省 \"kurobridge-pure\"。",
        "    \"server\": {",
        "        \"id\": \"${serverId}\"",
        "    },",
        "",
        "    // WS 监听段：对端连入点。整段缺省 = 动态端口 + 全部接口；只配 host 不配 port 也合法。",
        "    // 端口建议避开 25565（MC）与 25575（RCON）；端口被占则启动失败退出。",
        "    \"ws\": {",
        "        \"host\": \"${wsHost}\",",
        "        \"port\": ${wsPort}",
        "    }",
        "}",
        "",
    };

    private ConfigBootstrap() {}

    /**
     * 解析配置路径（ADR-040）：config.jsonc 优先；缺失时回退旧版 config.json；两者皆缺返回
     * config.jsonc（首启生成目标）。启用期与 reload 共用同一解析，避免双权威。
     */
    public static Path resolveConfigFile(Path dataFolder) {
        Path jsonc = dataFolder.resolve(CONFIG_FILE_NAME);
        if (Files.exists(jsonc)) {
            return jsonc;
        }
        Path legacy = dataFolder.resolve(LEGACY_CONFIG_FILE_NAME);
        if (Files.exists(legacy)) {
            return legacy;
        }
        return jsonc;
    }

    /** 是否落在旧版 config.json 上（调用方据此提示改名以获得注释模板）。 */
    public static boolean isLegacyConfig(Path file) {
        Path name = file.getFileName();
        return name != null && LEGACY_CONFIG_FILE_NAME.equals(name.toString());
    }

    /**
     * 文件缺失则生成带注释的默认配置（父目录一并创建），返回是否新生成（false = 既有文件原样不动）。
     * 目录创建失败/生成 IO 失败抛 ConfigException 指路；文件已存在（含 CREATE_NEW 并发竞争）不动字节。
     */
    public static boolean ensureConfigFile(Path file) {
        if (Files.exists(file)) {
            return false; // 既有文件不动（含 reload 语义：运行期不重新生成）
        }
        Path parent = file.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException directoryFailure) {
            throw new ConfigException(
                    String.format("配置目录创建失败：%s（%s）——请检查写权限（父路径被同名文件阻挡也会导致）", parent, directoryFailure),
                    directoryFailure);
        }
        try {
            writeNewOnly(file);
            return true;
        } catch (FileAlreadyExistsException raced) {
            return false; // CREATE_NEW 竞争失败 = 别处已生成/已存在，退回正常加载既有文件
        } catch (IOException ioFailure) {
            throw new ConfigException(String.format("配置文件生成失败：%s（%s）——请检查目录写权限，或手工创建该文件", file, ioFailure), ioFailure);
        }
    }

    /** 生成带注释的默认配置文本（JSONC；公开供测试断言形状与回环解析）。 */
    public static String defaultConfigJsonc() {
        StringBuilder text = new StringBuilder();
        for (String line : TEMPLATE_LINES) {
            text.append(line).append('\n');
        }
        return text.toString()
                .replace("${token}", randomToken())
                .replace("${serverId}", PureConfig.DEFAULT_SERVER_ID)
                .replace("${wsHost}", DEFAULT_WS_HOST)
                .replace("${wsPort}", Integer.toString(DEFAULT_WS_PORT));
    }

    /**
     * token 必配门禁（D2，PureWsServer 构造之前调用）：token 空（缺失或 ""）→ ConfigException，
     * 文案指路文件路径 + 设非空令牌 + 对端须一致 + 本机调试可用任意非空串。
     */
    public static void requireToken(PureConfig config, Path file) {
        if (!config.token().isEmpty()) {
            return;
        }
        throw new ConfigException(String.format(
                "token 未配置（缺失或空串），拒绝启动 WS 监听：%s%n" + "指路：在 token 字段设置非空令牌；对端须配置同一令牌；仅本机调试可用任意非空串（如 dev）。", file));
    }

    /** CREATE_NEW 落盘（防覆盖）；原子性交给 CREATE_NEW 语义本身。 */
    private static void writeNewOnly(Path file) throws IOException {
        // 不用 Files.writeString（Java 11）：Java 8 基线（ADR-038 对齐主仓 universal）走字节写入
        Files.write(
                file,
                defaultConfigJsonc().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    /** 16 字节 SecureRandom → 32 位小写 hex（D3；只落盘不回显）。不用 HexFormat（Java 17）。 */
    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(TOKEN_BYTES * 2);
        for (byte item : bytes) {
            hex.append(String.format("%02x", item));
        }
        return hex.toString();
    }
}
