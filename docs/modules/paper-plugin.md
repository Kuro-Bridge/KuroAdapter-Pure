# Paper 插件:生命周期与事件接线

`:paper` 是适配薄壳——零协议逻辑,只做 Bukkit 桥接(装配、事件、命令、调度)。主类 `com.kurobridge.pure.KuroBridgePurePlugin`;业务装配核心为 `PaperRelay`(实现 `BusinessHooks`)。

## 插件清单(paper-plugin.yml)

```yaml
name: KuroBridgePure
version: 0.1.0
main: com.kurobridge.pure.KuroBridgePurePlugin
api-version: "1.21"
load: STARTUP
```

- 权限节点:`kurobridge.relay`(default true,聊天转发静音位,见下)、`kurobridge.reload`(default op,控制台恒可)。
- paper-plugin.yml 在 Paper 1.21.4 **不支持 commands 块**,命令经 Lifecycle/Brigadier 注册(见 [paper-commands.md](paper-commands.md))。

## onEnable 装配顺序

fail-fast 贯穿全程:**非法配置、空 token、端口绑定失败一律 SEVERE 后中止启用,不静默降级、不给半工作状态**。

1. **命令注册**:LifecycleEvents.COMMANDS 挂载 `/kurobridge`(事件在全部插件启用完成后触发,重注册幂等);
2. **配置引导**:建数据目录 → `ConfigBootstrap.ensureConfigFile`(缺失则生成安全默认)→ `ConfigLoader.load` 单一权威解析 → `ConfigBootstrap.requireToken`(空 token 拒绝监听)。任何 `ConfigException` → SEVERE 后抛出中止([core-config.md](core-config.md));
3. **组件装配**:`ConfigBindingStore`、`PluginKbLogger`、`PaperCommandDispatcher`、`PaperRelay`(注入 `ForwardRules.EMPTY` + `AdminTable`)、`PaperWhitelistGateway`(结构化未来用)、`ExecutorTimeoutScheduler`;
4. **`SessionContext`**:serverId = 配置 `server.id`;serverVersion = 插件版本(`getPluginMeta().getVersion()`,即 paper-plugin.yml 的 0.1.0);token;`bindings::boundChannels` 快照供应者;`ServerTimeouts.defaults()`(hello 10s / idle 30s);`BukkitBusinessScheduler`(主线程投递);
5. **启动 WS 服务端**:`ws.host`/`ws.port` 为 null 时分别落到全部接口 / 端口 0(动态);`startAndWait()` 失败(端口被占/绑定超时)→ SEVERE 指路修改 `ws.port`/`ws.host` 后重启,**不回退动态端口**——回退会静默破坏对端静态 url;
6. **落字段**:`server` / `bindings` / `relay` / `config` 仅在启动成功后赋值(失败路径 onDisable 不误关半启动服务端);
7. **接线**:`relay.attach(server)` → `bindings.addChangeListener(relay::bindingsUpdated)` → 注册三个事件监听器;
8. 就绪日志(serverId / host / 实际端口 / 绑定频道 / 鉴权状态)。

## onDisable

顺序:`server.shutdown()`(已握手对端 close 1001 `server shutdown`)→ `timeoutScheduler.close()`(daemon 定时器收线)→ 各字段置 null。

## 事件接线

| 监听器 | Bukkit 事件 | 线程 | 行为 |
| :--- | :--- | :--- | :--- |
| `ChatListener` | `AsyncChatEvent` | **异步** | 玩家无 `kurobridge.relay` 权限(含 negate)→ 不上报(静音出口);否则 `relay.gameChat`(Component 经 `PlainText` 转纯文本)。控制台 `say` 等非玩家源不在事件入口,显式不转发 |
| `ConnectionListener` | `PlayerJoinEvent` | 主线程 | `relay.playerJoin` + `pushStatus(快照)` |
| `ConnectionListener` | `PlayerQuitEvent` | 主线程 | `relay.playerLeave` + `pushStatus(剔除退出者后的快照)` |
| `DeathListener` | `PlayerDeathEvent` | 主线程 | `deathMessage` 为 null(`/kill` 等场景)时空串兜底后 `relay.playerDeath`(协议允许 message 空串) |

## 游戏 → 平台:`PaperRelay` 出帧

- `gameChat` / `playerJoin` / `playerLeave` / `playerDeath` 走 `fanout`:遍历绑定表,**每个绑定频道一帧**(`chat` / `join` / `leave` / `death`),经 `PureWsServer.broadcast` 广播;无绑定或无已握手对端则自然不出帧。`ForwardRules.shouldForwardToPlatform` 为二次策略位,当前恒放行。
- **`status` 不走绑定频道**:帧体无 channel 字段(全服状态),`pushStatus` 直接 `broadcast`。快照与推送同入口(缓存与广播同源,`query status` 读同一快照,不维护两份状态)。
- `bindingsUpdated`:绑定表变化 → 广播 `bindings_updated`(完整列表)。
- status 快照数据源:`tps` = `Bukkit.getTPS()[0]`(1 分钟窗口),clamp 到 >= 0 并保留 1 位小数;`onlinePlayers` = 在线数;`uptimeSeconds` = JVM uptime(`RuntimeMXBean`)。
- **quit 窗口在线数口径**:`PlayerQuitEvent` 触发时退出者尚未移出 `getOnlinePlayers()` 列表,quit 路径按 UUID 剔除退出者后计数(`StatusCounts.countExcluding`,双向稳健:退出者已不在列表则不重复剔除)——推送值 = 事件后的真实在线数。

## 平台 → 游戏:`BusinessHooks` 入站

回调经 `BukkitBusinessScheduler` 派发,**已在主线程**(线程模型见 [threading.md](../architecture/threading.md)):

- `onPlatformChat`:绑定频道过滤(未绑定 → debug 丢弃)+ `ForwardRules.shouldForwardToGame` 判定 → `Bukkit.broadcast(Component.text("<sender> content"))` 全服广播一行纯文本,无前缀/颜色定制位。
- `onCommand`:管理员判定(`AdminTable`,管理员判定先于执行)→ 非管理员回 `failure("forbidden")` 并 warn;管理员透传 `PaperCommandDispatcher.execute`(全链见 [paper-commands.md](paper-commands.md))。无命令白名单——管理员即可执行任意命令。
- `latestStatus`:`query kind=status` 的快照来源,尚未推送过状态时为空(协议层回 `no status yet`)。

## 其余适配件

- `PluginKbLogger`:`KbLogger` → 插件 JUL logger 适配(`:core` 日志经插件前缀落 Paper 控制台)。
- `PlainText`:`PlainComponentSerializer.plain()` 做 Component → 纯文本(paper-api 1.21.4 只暴露 plain 序列化器;deprecation 局部压制,与主仓同款取舍)。
