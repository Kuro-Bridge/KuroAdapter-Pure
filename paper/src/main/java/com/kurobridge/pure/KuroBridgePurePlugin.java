// :paper 插件主类：生命周期装配（配置 → 绑定/调度/hooks → PureWsServer → 监听器注册）
package com.kurobridge.pure;

import com.kurobridge.pure.core.business.ConfigBindingStore;
import com.kurobridge.pure.core.business.ConfigException;
import com.kurobridge.pure.core.business.ConfigLoader;
import com.kurobridge.pure.core.business.ForwardRules;
import com.kurobridge.pure.core.business.PureConfig;
import com.kurobridge.pure.core.server.DirectBusinessScheduler;
import com.kurobridge.pure.core.server.ExecutorTimeoutScheduler;
import com.kurobridge.pure.core.server.PureWsServer;
import com.kurobridge.pure.core.server.ServerTimeouts;
import com.kurobridge.pure.core.server.SessionContext;
import com.kurobridge.pure.paper.ChatListener;
import com.kurobridge.pure.paper.ConnectionListener;
import com.kurobridge.pure.paper.DeathListener;
import com.kurobridge.pure.paper.PaperRelay;
import com.kurobridge.pure.paper.PluginKbLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KuroBridgePure 插件入口（零协议逻辑——协议层与业务骨架在 :core，本类只做装配）。
 *
 * <p>onEnable：dataFolder 建目录 → 读配置（config.json 存在则加载；**非法配置 SEVERE 后中止
 * 启用**，不静默降级成「无 token 全接口监听」的安全敞口）→ 组装绑定表/调度器/hooks →
 * 启动 PureWsServer（动态端口时日志实际端口）→ 注册监听器三件 + 绑定变更监听。
 * onDisable：server.shutdown()（已握手对端 close 1001 "server shutdown"）+ 定时器收线。
 */
public final class KuroBridgePurePlugin extends JavaPlugin {

    /** hello_ack 上报的服务器标识（主仓 embedded 同为常量 kurobridge-spike；配置化随 STATUS 第 5 条）。 */
    private static final String SERVER_ID = "kurobridge-pure";

    private PureWsServer server;
    private ExecutorTimeoutScheduler timeoutScheduler;

    @Override
    public void onEnable() {
        PureConfig config = loadConfig();

        ConfigBindingStore bindings = new ConfigBindingStore(config.channels());
        PaperRelay relay = new PaperRelay(bindings, ForwardRules.EMPTY);
        PluginKbLogger logger = new PluginKbLogger(getLogger());
        timeoutScheduler = new ExecutorTimeoutScheduler();
        SessionContext context = new SessionContext(
                SERVER_ID,
                getPluginMeta().getVersion(),
                config.token(),
                bindings::boundChannels,
                ServerTimeouts.defaults(),
                timeoutScheduler,
                DirectBusinessScheduler.INSTANCE, // 阶段 2 换 Bukkit 主线程投递（BukkitBusinessScheduler）
                relay,
                logger);

        PureConfig.WsListen ws = config.ws();
        String host = ws == null || ws.host() == null ? null : ws.host();
        int port = ws == null || ws.port() == null ? 0 : ws.port();
        server = new PureWsServer(host, port, context);
        int actualPort;
        try {
            actualPort = server.startAndWait();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WS 服务端启动被中断", interrupted);
        }
        relay.attach(server);
        bindings.addChangeListener(relay::bindingsUpdated);

        Bukkit.getPluginManager().registerEvents(new ChatListener(relay), this);
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(relay), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(relay), this);

        int finalPort = actualPort;
        getLogger()
                .info(() -> "就绪：serverId=" + SERVER_ID + "，host="
                        + (host == null ? "全部接口" : host) + "，端口=" + finalPort
                        + (port == 0 ? "（动态分配）" : "") + "，绑定频道=" + config.channels()
                        + "，鉴权=" + (config.token().isEmpty() ? "关闭（空 token）" : "开启"));
    }

    @Override
    public void onDisable() {
        if (server != null) {
            try {
                server.shutdown(); // 已握手对端 close 1001 "server shutdown"，随后停服务端
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            server = null;
        }
        if (timeoutScheduler != null) {
            timeoutScheduler.close();
            timeoutScheduler = null;
        }
    }

    /** 配置读取：存在则加载（非法即中止启用），不存在则缺省 + 日志说明。 */
    private PureConfig loadConfig() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("无法创建插件数据目录：" + getDataFolder());
        }
        Path configFile = getDataFolder().toPath().resolve("config.json");
        if (!Files.exists(configFile)) {
            getLogger().info(() -> "未找到 " + configFile + "，使用缺省配置：无绑定频道、不鉴权、" + "动态端口全部接口（游戏事件不出帧，直至写入 channels 绑定）");
            return PureConfig.defaults();
        }
        try {
            PureConfig config = ConfigLoader.load(configFile);
            getLogger().info(() -> "已加载配置：" + configFile);
            return config;
        } catch (ConfigException invalid) {
            // 错误配置不静默降级（避免无 token 全接口监听）：SEVERE 后中止启用，修正后重启
            getLogger().log(Level.SEVERE, "配置不合法，插件停用：" + invalid.getMessage(), invalid);
            throw invalid;
        }
    }
}
