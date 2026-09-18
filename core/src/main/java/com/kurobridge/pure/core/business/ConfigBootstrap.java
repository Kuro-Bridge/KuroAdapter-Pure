// 首启配置生成（D1/D3/D7）+ token 必配门禁（D2）：纯文件/纯函数逻辑，Bukkit 壳保持薄
package com.kurobridge.pure.core.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 配置引导（纯逻辑，无 Bukkit API；生成件即文档）：
 *
 * <ul>
 *   <li>{@link #ensureConfigFile(Path)}：文件缺失时生成安全默认配置落盘（D7 形状：channels []、
 *       token 32 位 hex 随机、admins []、server.id 默认、ws 127.0.0.1:25580）。写入用 CREATE_NEW
 *       语义防覆盖（等价主仓 wx）：并发竞争落到既有文件时静默退回，随后照常走 ConfigLoader
 *       加载（单一权威，不为生成件开第二解析路径）。生成仅发生在启用期；运行期 reload 的
 *       文件缺失不在此处补写（调用方直接 load 并报错）。
 *   <li>{@link #requireToken(PureConfig, Path)}：token 空（缺失或 ""）→ ConfigException 指路后
 *       由调用方中止启用（D2 安全默认，对主仓「WARN 不阻断」的有意偏差）。
 * </ul>
 */
public final class ConfigBootstrap {

    /** 生成件的 ws.host（环回 = 安全默认，也是对端静态 url 的开箱即用值）。 */
    public static final String DEFAULT_WS_HOST = "127.0.0.1";

    /** 生成件的 ws.port（与 koishi-dev 部署值及 SMOKE 登记一致的 25580）。 */
    public static final int DEFAULT_WS_PORT = 25580;

    /** D3：随机 token = 16 字节 SecureRandom → 32 位 hex。 */
    private static final int TOKEN_BYTES = 16;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigBootstrap() {}

    /**
     * 文件缺失则生成安全默认配置（父目录一并创建），返回是否新生成（false = 既有文件原样不动）。
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

    /** 生成安全默认配置文本（D7 形状，Jackson pretty print；公开供测试断言形状）。 */
    public static String defaultConfigJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.putArray("channels"); // 无绑定：平台消息不进游戏，直至服主写入频道
        root.put("token", randomToken());
        root.putArray("admins");
        root.putObject("server").put("id", PureConfig.DEFAULT_SERVER_ID);
        root.putObject("ws").put("host", DEFAULT_WS_HOST).put("port", DEFAULT_WS_PORT);
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + System.lineSeparator();
        } catch (IOException unreachable) {
            throw new IllegalStateException("内存树序列化不应失败", unreachable);
        }
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
        Files.writeString(
                file,
                defaultConfigJson(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    /** 16 字节 SecureRandom → 32 位小写 hex（D3；只落盘不回显）。 */
    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
