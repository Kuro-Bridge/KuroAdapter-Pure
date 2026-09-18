// :paper 插件主类：生命周期装配（配置引导 → 绑定/调度/hooks → PureWsServer → 监听器/命令注册）
package com.kurobridge.pure;

import com.kurobridge.pure.core.business.ConfigBindingStore;
import com.kurobridge.pure.core.business.ConfigBootstrap;
import com.kurobridge.pure.core.business.ConfigException;
import com.kurobridge.pure.core.business.ConfigLoader;
import com.kurobridge.pure.core.business.ConfigReloadReport;
import com.kurobridge.pure.core.business.ForwardRules;
import com.kurobridge.pure.core.business.PureConfig;
import com.kurobridge.pure.core.server.ExecutorTimeoutScheduler;
import com.kurobridge.pure.core.server.PureWsServer;
import com.kurobridge.pure.core.server.ServerTimeouts;
import com.kurobridge.pure.core.server.SessionContext;
import com.kurobridge.pure.paper.AdminTable;
import com.kurobridge.pure.paper.BukkitBusinessScheduler;
import com.kurobridge.pure.paper.ChatListener;
import com.kurobridge.pure.paper.ConnectionListener;
import com.kurobridge.pure.paper.DeathListener;
import com.kurobridge.pure.paper.KurobridgeCommand;
import com.kurobridge.pure.paper.PaperCommandDispatcher;
import com.kurobridge.pure.paper.PaperRelay;
import com.kurobridge.pure.paper.PaperWhitelistGateway;
import com.kurobridge.pure.paper.PluginKbLogger;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KuroBridgePure 插件入口（零协议逻辑——协议层与业务骨架在 :core，本类只做装配）。
 *
 * <p>onEnable：命令注册器挂载 → dataFolder 建目录 → 配置引导（config.json 缺失则生成安全默认，
 * 随后经 ConfigLoader 单一权威解析；**非法配置/空 token/端口绑定失败一律 SEVERE 后中止启用**，
 * 不静默降级、不给半工作状态）→ 组装绑定表/调度器/hooks → 启动 PureWsServer（动态端口时日志
 * 实际端口）→ 注册监听器三件 + 绑定变更监听。onDisable：server.shutdown()（已握手对端
 * close 1001 "server shutdown"）+ 定时器收线。
 *
 * <p>运行期 `/kurobridge reload`（{@link #performKurobridgeReload}）：读盘重解析，失败保留旧
 * 配置；成功则 channels/admins 热更（bindings_updated 自动广播 + AdminTable 原子换入），
 * token/ws/server.id 进程固定，回执明示待重启字段。
 */
public final class KuroBridgePurePlugin extends JavaPlugin {

    /** 运行中配置（reload 的对比基准与热更写入位；null = 尚未完成启用，reload 壳守卫拒绝）。 */
    private volatile PureConfig config;

    private PureWsServer server;
    private ExecutorTimeoutScheduler timeoutScheduler;
    private PaperWhitelistGateway whitelistGateway;
    private ConfigBindingStore bindings;
    private PaperRelay relay;

    @Override
    public void onEnable() {
        registerKurobridgeCommand();
        Path configFile = getDataFolder().toPath().resolve("config.json");

        PureConfig config;
        try {
            config = loadOrCreateConfig(configFile);
        } catch (ConfigException invalid) {
            // D8 fail-fast：非法配置/生成失败/空 token 不静默降级——SEVERE 后中止启用（修正后重启）
            getLogger().log(Level.SEVERE, invalid.getMessage(), invalid);
            throw invalid;
        }

        ConfigBindingStore bindings = new ConfigBindingStore(config.channels());
        PluginKbLogger logger = new PluginKbLogger(getLogger());
        PaperCommandDispatcher commandDispatcher = new PaperCommandDispatcher(this);
        PaperRelay relay = new PaperRelay(
                bindings, ForwardRules.EMPTY, new AdminTable(config.admins()), commandDispatcher, logger);
        whitelistGateway = new PaperWhitelistGateway(commandDispatcher); // 结构化未来用（ADR-027 同机制）
        timeoutScheduler = new ExecutorTimeoutScheduler();
        SessionContext context = new SessionContext(
                config.serverId(),
                getPluginMeta().getVersion(),
                config.token(),
                bindings::boundChannels,
                ServerTimeouts.defaults(),
                timeoutScheduler,
                new BukkitBusinessScheduler(this), // 业务回调投递 Bukkit 主线程（Bukkit API 非线程安全）
                relay,
                logger);

        PureConfig.WsListen ws = config.ws();
        String host = ws == null || ws.host() == null ? null : ws.host();
        int port = ws == null || ws.port() == null ? 0 : ws.port();
        PureWsServer wsServer = new PureWsServer(host, port, context);
        int actualPort;
        try {
            actualPort = wsServer.startAndWait();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WS 服务端启动被中断", interrupted);
        } catch (IllegalStateException startupFailed) {
            // 端口被占/绑定超时：SEVERE 指路改 config 后重启。不回退动态端口——回退会静默破坏对端静态 url
            getLogger()
                    .severe(String.format(
                            "WS 服务端启动失败：%s。指路：修改 %s 的 ws.port / ws.host（或释放被占端口）后重启服务器。",
                            startupFailed.getMessage(), configFile));
            throw startupFailed;
        }

        this.server = wsServer; // 启动成功才落字段（失败路径 onDisable 不误关半启动服务端）
        this.bindings = bindings;
        this.relay = relay;
        this.config = config;

        relay.attach(wsServer);
        bindings.addChangeListener(relay::bindingsUpdated);

        Bukkit.getPluginManager().registerEvents(new ChatListener(relay), this);
        Bukkit.getPluginManager().registerEvents(new ConnectionListener(relay), this);
        Bukkit.getPluginManager().registerEvents(new DeathListener(relay), this);

        int finalPort = actualPort;
        getLogger()
                .info(() -> "就绪：serverId=" + config.serverId() + "，host="
                        + (host == null ? "全部接口" : host) + "，端口=" + finalPort
                        + (port == 0 ? "（动态分配）" : "") + "，绑定频道=" + config.channels()
                        + "，鉴权=开启（非空 token）");
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
        whitelistGateway = null;
        bindings = null;
        relay = null;
        config = null;
    }

    /** 白名单网关（结构化调用位；须在 Bukkit 主线程使用——见 PaperWhitelistGateway 契约）。 */
    public PaperWhitelistGateway whitelistGateway() {
        return whitelistGateway;
    }

    /**
     * 配置引导（D1/D2/D8）：目录缺失建目录 → 文件缺失生成安全默认（CREATE_NEW 防覆盖，token
     * 随机 32 hex）→ ConfigLoader 单一权威解析 → 空 token 门禁（拒绝监听，指路文案）。任何一步
     * 不合格抛 ConfigException，由 onEnable 统一 SEVERE 后中止启用。
     */
    private PureConfig loadOrCreateConfig(Path configFile) {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new ConfigException("无法创建插件数据目录：" + getDataFolder());
        }
        if (ConfigBootstrap.ensureConfigFile(configFile)) {
            getLogger()
                    .info(() -> "已生成默认配置：" + configFile + "（token 为随机 32 位十六进制，对端须从该文件"
                            + "抄取并配置同一 token；ws 默认环回 " + ConfigBootstrap.DEFAULT_WS_HOST + ":"
                            + ConfigBootstrap.DEFAULT_WS_PORT + "）");
        }
        PureConfig config = ConfigLoader.load(configFile);
        getLogger().info(() -> "已加载配置：" + configFile);
        ConfigBootstrap.requireToken(config, configFile); // 空串 → ConfigException 指路（D2 安全默认）
        return config;
    }

    /**
     * /kurobridge 注册器挂载（LifecycleEvents.COMMANDS + BasicCommand）：paper-plugin.yml 的
     * commands 块在 Paper 1.21.4 不受支持（PluginMeta 无 commands 字段），故走 Brigadier API。
     * COMMANDS 事件在全部插件启用完成后触发（重注册幂等）；启用失败的实例由
     * {@link #performKurobridgeReload} 的未启用守卫兜底，不产生半工作命令。
     */
    private void registerKurobridgeCommand() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar()
                .register("kurobridge", "KuroBridge 互通桥管理", List.of(), new KurobridgeCommand(this)));
    }

    /**
     * /kurobridge reload（Brigadier 主线程同步派发）：读盘→ConfigLoader 解析；失败→**保留旧配置**
     * 回显原因（文件缺失不重新生成——生成仅发生在启用时）；成功→绑定表 replace（集合变化自动触发
     * 既有 bindings_updated 广播）+ AdminTable 重建原子换入 relay → 按 D6 热冷矩阵回执（仅列出
     * 有变化的字段；token/ws/server.id 进程固定，明示待重启）。
     */
    public void performKurobridgeReload(CommandSender sender) {
        PureConfig current = config;
        if (current == null || relay == null || bindings == null) {
            sender.sendMessage("KuroBridgePure 未启用（配置非法或启动失败），无法重载");
            return;
        }
        Path configFile = getDataFolder().toPath().resolve("config.json");
        PureConfig next;
        try {
            next = ConfigLoader.load(configFile);
        } catch (ConfigException invalid) {
            getLogger().log(Level.WARNING, "重载失败，保留旧配置：" + invalid.getMessage(), invalid);
            sender.sendMessage("已保留旧配置，重载失败：" + invalid.getMessage());
            return;
        }

        bindings.replace(next.channels()); // 集合变化 → 自动触发 bindings_updated 广播链路
        if (!next.admins().equals(current.admins())) {
            relay.replaceAdmins(new AdminTable(next.admins()));
        }
        this.config = next;

        ConfigReloadReport report = ConfigReloadReport.diff(current, next);
        sender.sendMessage("已重载配置：" + configFile);
        sender.sendMessage("已热更：" + joinOrNone(report.hotApplied()));
        sender.sendMessage("待重启（重启后生效）：" + joinOrNone(report.restartPending()));
    }

    private static String joinOrNone(List<String> items) {
        return items.isEmpty() ? "无" : String.join("、", items);
    }
}
