# Paper 插件:生命周期与事件接线

`:paper` 是适配薄壳——零协议逻辑,只做 Bukkit 桥接(装配、事件、命令、调度)。主类 `com.kurobridge.pure.KuroBridgePurePlugin`;业务装配核心为 `PaperRelay`(实现 `BusinessHooks`)。

## 插件清单(经典 plugin.yml,ADR-038 对齐主仓 universal 基线)

```yaml
name: KuroBridgePure
version: '0.1.0'
main: com.kurobridge.pure.KuroBridgePurePlugin
api-version: '1.13'
load: STARTUP
```

- 经典 plugin.yml 从 1.12.2 到 26.3 全版本可加载;paper-plugin.yml 是 1.19.4+ 专属格式,旧服不可见。api-version 是 1.13+ 的下限标记,1.12.2 忽略未知键。
- 权限节点:`kurobridge.relay`(default true,聊天转发静音位,见下)、`kurobridge.reload`(default op,控制台恒可)。
- commands 块声明 `kurobridge` 命令(usage/description/permission),经典注册见 [paper-commands.md](paper-commands.md)。

## onEnable 装配顺序

fail-fast 贯穿全程:**非法配置、空 token、端口绑定失败一律 SEVERE 后中止启用,不静默降级、不给半工作状态**。

1. **命令注册**:`getCommand("kurobridge")` 挂 `KurobridgeCommand` 为 Executor + TabCompleter(plugin.yml commands 块声明;缺失即抛,构建产物不完整不容隐忍);
2. **配置引导**:建数据目录 → `ConfigBootstrap.ensureConfigFile`(缺失则生成安全默认)→ `ConfigLoader.load` 单一权威解析 → `ConfigBootstrap.requireToken`(空 token 拒绝监听)。任何 `ConfigException` → SEVERE 后抛出中止([core-config.md](core-config.md));
3. **组件装配**:`ConfigBindingStore`、`PluginKbLogger`、`PaperCommandDispatcher`、`PaperRelay`(注入 `ForwardRules.EMPTY` + `AdminTable`)、`PaperWhitelistGateway`(结构化未来用)、`ExecutorTimeoutScheduler`;
4. **`SessionContext`**:serverId = 配置 `server.id`;serverVersion = 插件版本(`getDescription().getVersion()`,26.x 标弃用但全版本存留,getPluginMeta 1.19+ 才有);token;`bindings::boundChannels` 快照供应者;`ServerTimeouts.defaults()`(hello 10s / idle 30s);`BukkitBusinessScheduler`(主线程投递);
5. **启动 WS 服务端**:`ws.host`/`ws.port` 为 null 时分别落到全部接口 / 端口 0(动态);`startAndWait()` 失败(端口被占/绑定超时)→ SEVERE 指路修改 `ws.port`/`ws.host` 后重启,**不回退动态端口**——回退会静默破坏对端静态 url;
6. **落字段**:`server` / `bindings` / `relay` / `config` 仅在启动成功后赋值(失败路径 onDisable 不误关半启动服务端);
7. **接线**:`relay.attach(server)` → `bindings.addChangeListener(relay::bindingsUpdated)` → 注册事件监听器(聊天按 `Compat` 探测双路径,见下);
8. 就绪日志(serverId / host / 实际端口 / 绑定频道 / 鉴权状态)。

## onDisable

顺序:`server.shutdown()`(已握手对端 close 1001 `server shutdown`)→ `timeoutScheduler.close()`(daemon 定时器收线)→ 各字段置 null。

## 事件接线

聊天监听按运行期探测(`Compat`)二选一,**不双注册**(现代服挂 legacy 监听会把整服聊天管线降级,Paper 有全局告警):

| 监听器 | Bukkit 事件 | 生效服 | 线程 | 行为 |
| :--- | :--- | :--- | :--- | :--- |
| `ChatListener` | `AsyncChatEvent` | Paper 1.19+ | **异步** | 玩家无 `kurobridge.relay` 权限(含 negate)→ 不上报(静音出口);否则 `relay.gameChat`(Component 经 `PlainText` 兼容链转纯文本)。控制台 `say` 等非玩家源不在事件入口,显式不转发 |
| `LegacyChatListener` | `AsyncPlayerChatEvent` | 1.12.2 ~ 1.18.2 | **异步** | 与 `ChatListener` 同语义,消息载体为 String(无需序列化) |
| `ConnectionListener` | `PlayerJoinEvent` | 全版本 | 主线程 | `relay.playerJoin` + `pushStatus(快照)` |
| `ConnectionListener` | `PlayerQuitEvent` | 全版本 | 主线程 | `relay.playerLeave` + `pushStatus(剔除退出者后的快照)` |
| `DeathListener` | `PlayerDeathEvent` | 全版本 | 主线程 | `getDeathMessage()` String 路径(1.12.2 → 26.3 全版本存留)剥 legacy 颜色码后 `relay.playerDeath`;null(`/kill` 等场景)时空串兜底(协议允许 message 空串) |

## 游戏 → 平台:`PaperRelay` 出帧

- `gameChat` / `playerJoin` / `playerLeave` / `playerDeath` 走 `fanout`:遍历绑定表,**每个绑定频道一帧**(`chat` / `join` / `leave` / `death`),经 `PureWsServer.broadcast` 广播;无绑定或无已握手对端则自然不出帧。`ForwardRules.shouldForwardToPlatform` 为二次策略位,当前恒放行。
- **`status` 不走绑定频道**:帧体无 channel 字段(全服状态),`pushStatus` 直接 `broadcast`。快照与推送同入口(缓存与广播同源,`query status` 读同一快照,不维护两份状态)。
- `bindingsUpdated`:绑定表变化 → 广播 `bindings_updated`(完整列表)。
- status 快照数据源:`tps` = `Bukkit.getTPS()[0]`(1 分钟窗口),clamp 到 >= 0 并保留 1 位小数;`onlinePlayers` = 在线数;`uptimeSeconds` = JVM uptime(`RuntimeMXBean`)。
- **quit 窗口在线数口径**:`PlayerQuitEvent` 触发时退出者尚未移出 `getOnlinePlayers()` 列表,quit 路径按 UUID 剔除退出者后计数(`StatusCounts.countExcluding`,双向稳健:退出者已不在列表则不重复剔除)——推送值 = 事件后的真实在线数。

## 平台 → 游戏:`BusinessHooks` 入站

回调经 `BukkitBusinessScheduler` 派发,**已在主线程**(线程模型见 [threading.md](../architecture/threading.md)):

- `onPlatformChat`:绑定频道过滤(未绑定 → debug 丢弃)+ `ForwardRules.shouldForwardToGame` 判定 → `Broadcasts.broadcast("<sender> content")` 全服广播一行纯文本,无前缀/颜色定制位。广播经跨版本分发:`Compat` 探测 Adventure 可用走 `Bukkit.broadcast(Component)`(调用点隔离在独立 holder 类,无 Adventure 的服务器永不加载),否则(1.12.2)回退 `Bukkit.broadcastMessage(String)`。
- `onCommand`:管理员判定(`AdminTable`,管理员判定先于执行)→ 非管理员回 `failure("forbidden")` 并 warn;管理员透传 `PaperCommandDispatcher.execute`(全链见 [paper-commands.md](paper-commands.md))。无命令白名单——管理员即可执行任意命令。
- `latestStatus`:`query kind=status` 的快照来源,尚未推送过状态时为空(协议层回 `no status yet`)。

## 其余适配件

- `PluginKbLogger`:`KbLogger` → 插件 JUL logger 适配(`:core` 日志经插件前缀落 Paper 控制台)。
- `PlainText`:Component → 纯文本的跨 Adventure 版本兼容链——现代 `PlainTextComponentSerializer`(4.15+,Paper 1.20.6+ 捆绑)失败则回退旧 `PlainComponentSerializer`(`LegacyPlain` 隔离持有者,覆盖 1.19 ~ 1.21.x 的 adventure 4.x);隔离 holder + 首用解析缓存,任一版本缺失只烧掉自己的 holder(主仓 26.3 序列化器被删事故的防御,ADR-038)。
