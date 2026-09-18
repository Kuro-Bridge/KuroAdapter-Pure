# :paper 接线设计书（PAPER-WIRING，2026-09-18）

> 目标：把 `:paper` 从空壳做到「能用」——STATUS「下一阶段清单」第 1-3 条
> （事件接线 / BusinessScheduler Paper 实现 / 业务实现）。设计依据：
> 本仓 `docs/design.md`（分层与线程模型）+ 主仓只读范本
> （`platforms/je/paper` 的监听器与命令桥接、`bridge/core/src` 的 relay/server 语义）。
> 范围内不含：第 4 条真机联调（另线）、第 5 条配置落盘与 `/kurobridge reload`；
> 但第 5 条的**配置读取**（config.json 存在则加载，否则缺省 + 日志）在本线内。

## 0. 结论速览

| # | 议题 | 决策 |
|---|---|---|
| 1 | 广播缝隙 | `PureWsServer.broadcast(Frame):int`，仅已握手会话、返回送达数（对齐主仓 `sendToEstablished`） |
| 2 | 跨线程 send | `PeerSession.state` 提为 `volatile`（唯一必要修复）；传输层本身线程安全 |
| 3 | status 单一来源 | `:paper` 的 BusinessHooks 实现自持 `volatile StatusBody` 快照；「更新快照 + 广播」同一入口 `pushStatus` |
| 4 | 调度 | `BukkitBusinessScheduler` 包 `runTask`；disable 防御 = 捕获 + warn 丢弃；onCommand 包成 CompletableFuture 在主线程完成 |
| 5 | 白名单/管理员 | 对齐主仓 ADR-027：SSOT=MC 原生 whitelist；管理员判定在 dispatch 之前；无命令白名单 |
| 6 | ForwardRules | 绑定频道双向默认放行（=主仓 `forwarding.ts` 现行语义），留配置化未来 |
| 7 | BindingStore | `ConfigBindingStore`（PureConfig.channels 支撑 + replace 通知位）；变更来源 reload 落地前为空 |

## 1. 广播缝隙：PureWsServer.broadcast（议题 a）

主仓范本：`bridge/core/src/server.ts:184-189` `sendToEstablished`——遍历全部对端，
仅 `established` 者 `connection.send(text)`，计数返回；0 送达时 debug 日志。主仓的
`sendGameChat/sendJoin/...` 全部是「上层按频道逐帧调用 + sendToEstablished 发全部对端」。

本仓 `:core` 的 `PureWsServer` 只有 `sessions`（私有）与 `establishedCount()`，没有发帧出口；
`PeerSession.send(Frame)` 是 public 的单连接出口。补一个服务端级 API：

```java
/** 帧广播：发给全部已握手对端，返回送达数（对齐主仓 sendToEstablished 契约）。 */
public int broadcast(Frame frame)
```

- 实现：`FrameCodec.encode` 一次，遍历 `sessions.values()`，`session.send(text)` 为 true
  则计数；0 送达 debug（「无已握手对端，丢弃出帧」，对齐主仓文案）。
- **竞态兜底**：`PeerSession.send` 先查 `established() && isOpen()` 再发，两查与发之间存在
  关闭窗口，Java-WebSocket 对已断连接的 `send` 抛 `WebsocketNotConnectedException`
  （RuntimeException）。广播循环内逐会话 catch RuntimeException 计为未送达、不外抛——
  事件推送是尽力而为语义（主仓 ws 库 send 永不同步抛错，此处对齐其可观察行为）。
- 单元测试（沿用 `PureWsServerTest` 回环模式）：两个客户端一握一未握 → broadcast 仅已握手
  者收到、返回 1；握手数为 0 时返回 0。

### 1.1 PeerSession 跨线程 send 的逐字段分析（议题 a 续）

`PeerSession` 自述「WS 线程拥有全部连接状态（不加锁）」（PeerSession.java:26-27），但
`send(String)`/`send(Frame)` 的 javadoc 明写「游戏侧事件 fan-out 用」——阶段 1 起将从
**Bukkit 主线程**（join/quit/death/status）与**异步聊天线程**（AsyncChatEvent）调用。
逐字段核查：

| 字段 | 写者 | send 路径读者 | 结论 |
|---|---|---|---|
| `connection` | 仅构造器 | 是 | final + 经 `PureWsServer.sessions`（ConcurrentHashMap.put）发布，happens-before 成立；`WebSocketImpl.send` 内部 synchronized，**线程安全**（Java-WebSocket 官方支持任意线程 send） |
| `state` | WS 线程（handleHello/rejectHello/onClose） | 是（`established()`） | **需要 volatile**：无同步的跨线程读可能读到过期 `AWAITING_HELLO`，握手完成后的首帧被误丢。单字修复：`private volatile State state` |
| `peerId` | WS 线程（handleHello） | 否（仅 WS 线程日志用） | 不动 |
| `helloTimer` | WS 线程（arm/cancel）+ 定时器回调线程（null） | 否 | 不在 send 路径；竞态为「对已触发句柄 cancel（no-op）」，良性，维持现状（:core 既有边界，如实记录） |
| `idleTracker` | WS 线程 | 否 | 内部已 `AtomicReference`（线程安全自述成立） |

**最小修复 = 仅 `state` 提 volatile**。副作用收益：`establishedCount()` 与 `broadcast`
的非 WS 线程读同样获得可见性。类注释同步改述（“WS 线程拥有状态；跨线程仅经
volatile state + 线程安全 send 出口”）。

## 2. latestStatus 单一来源（议题 b）

主仓范本 `server.ts:158-161`：`sendStatus` 一肩挑「更新 query 缓存 + 推帧」——推送与缓存
同一入口，不存在两条独立更新路径。`query kind=status`（PeerSession.answerQuery →
`hooks.latestStatus()`）与本设计共用同一份快照。

本仓落地：`:paper` 的 `PaperRelay`（BusinessHooks 实现）自持 `volatile StatusBody
latestStatus`（null=从未推送 → 协议层回 `no status yet`，已实现）：

```java
/** status 单一入口：先落快照再广播（镜像主仓 server.sendStatus 的缓存+推送同源）。 */
public void pushStatus(StatusBody snapshot) {
    this.latestStatus = snapshot;
    server.broadcast(Frames.status(snapshot));
}
```

调用方仅 ConnectionListener（join/quit 后各一次，数据源同主仓 ConnectionListener.java:50-63：
`Bukkit.getTPS()[0]` clamp ≥0 保留 1 位小数、`getOnlinePlayers().size()`、
`ManagementFactory.getRuntimeMXBean().getUptime()/1000`）。快照在主线程写、WS 线程读
（query），volatile 保证可见性。

## 3. 调度语义（议题 c）

### 3.1 BukkitBusinessScheduler（阶段 2）

```java
public final class BukkitBusinessScheduler implements BusinessScheduler {
    // dispatch = Bukkit.getScheduler().runTask(plugin, task) 的包装
}
```

- 主仓范本：`NodeRequestHandler.java:39、53`——一切 Bukkit API 操作经
  `runTask(plugin, ...)` 调度回主线程。
- **disable 防御**：插件已 disable 时 `runTask` 抛 `IllegalPluginAccessException`
  （RuntimeException）。主仓范本（NodeRequestHandler.java:40-44、81-84）是捕获后**显式
  error 回执**；本仓 `dispatch` 契约是 void（回执无法经返回值传递），故语义为：
  捕获 + warn 日志 + **丢弃任务**。合理性：dispatch 失败只发生在 disable 窗口，而
  onDisable 同步先 `server.shutdown()`（close 1001），对端连接先行关闭——等待回执的
  command 由对端自身的 10s 超时兜底（peer-guide §5.1），服务端无悬挂义务。日志保证可观测。
- 阶段 1 先用 `DirectBusinessScheduler.INSTANCE` 占位（onEnable 可跑通全链事件出帧），
  阶段 2 换入并在提交中注明。

### 3.2 onCommand 的 CompletableFuture 包装（阶段 3）

```
WS 线程收 command 帧
  → PeerSession.dispatchCommand → businessScheduler.dispatch(task)   [:core 既有]
    → task 在 Bukkit 主线程执行：
        PaperRelay.onCommand(body)
          = new CompletableFuture
          → 主线程内同步完成：管理员判定（非管理员 → failure("forbidden")，不执行）
          → 执行命令（CollectingCommandSender 收集输出，主线程执行完才 complete）
    → future.whenComplete 由完成线程（=主线程）回调 → connection.send(command_result)
```

- 主线程执行完命令才回执 = 主仓 v0.3.0 语义（NodeRequestHandler.java:85-87 注释）。
- **异常回帧**：任务体内 catch RuntimeException → `future.completeExceptionally`，
  `PeerSession.invokeCommandHandler` 的 whenComplete 失败分支回
  `command_result {ok:false, error:<message>}`（:core 既有），对齐主仓
  NodeRequestHandler.java:67-71「命令本身抛异常 → 显式 error」。
- 注意任务体必须自行 catch——Bukkit 调度任务抛出的异常被 Bukkit 吞掉只打日志，
  future 会悬挂，对端只能等超时；自行 catch 转化为例外完成才能回帧。
- `connection.send`（主线程回调）经 §1.1 的 volatile state + Java-WebSocket 线程安全
  send，安全。

## 4. 白名单与管理员语义（议题 d，对齐主仓 ADR-027）

ADR-027（KuroAdapter/docs/DECISIONS.md:196-204）要点：
1. **SSOT = MC 原生 whitelist.json**，本仓不自建存储；
2. 白名单操作经 **CONSOLE 名义 dispatch** `whitelist add|remove|list` 收集输出；
3. **管理员判定在 dispatch 之前**：channel 命中且 userId 在 users 内，否则
   `{ok:false, error:"forbidden"}` 且**不 dispatch**（主仓 relay.ts:155-163）；
4. **没有命令白名单**——管理员即可透传任意命令（协议侧仅约定 command 无前导斜杠、
   执行者=控制台、对端超时 10s，见 KuroProtocol/docs/peer-guide.md:124、127-136）。

**grep 结论（先行核实）**：`:core` 内 `WhitelistGateway`/`BindingStore`/`ForwardRules`
除接口与 EMPTY 定义外**无任何入站调用方**（协议层 PeerSession 只依赖 SessionContext 的
hooks/scheduler）。故明确：**消费方 = `:paper` 的 onCommand 实现**（管理员判定 → 通用
`dispatchCommand`）；`WhitelistGateway` 的 Paper 实现（`PaperWhitelistGateway`）**复用同一
执行机制**（`whitelist add/remove/list` 字符串 dispatch + 输出收集），供结构化调用未来用，
本阶段无调用方但落地即语义正确。

落地件（主仓逐件镜像）：
- `AdminTable`：`isAdmin(channel, userId)`（主仓 admins.ts:38-42 语义）；数据源
  `PureConfig.admins`（`PureConfig.AdminMapping` 已与主仓 admins 结构同构，PureConfig.java:13）。
- `CollectingCommandSender`：CONSOLE 语义收集 sender（hasPermission/isOp 恒 true、消息全
  收集；主仓 CollectingCommandSender.java:18-38）。
- `VanillaFeedbackCapture`：Paper 的 VanillaCommandWrapper 拒绝自定义 sender 时回退真实
  console sender，输出经 log4j appender 收集（主仓 DEBT1-NOTES D1-04）。**需要
  `log4j-core` compileOnly**（运行期 Paper 服务端自带，与 paper-api 同属 compileOnly
  提供物；AGENTS.md 红线 2 的白名单措辞需补一笔——本设计书声明在案，AGENTS.md 归
  文档块，不在本线改）。
- 执行路径：`dispatchCommand(String): CommandResultBody`——runTask 内
  `Bukkit.dispatchCommand(sender, command)`，`CommandException` 且为 vanilla listener
  拒绝 → 回退 `Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)` + capture
  收集；其余异常显式 error。

## 5. ForwardRules 裁决（议题 e）

绑定频道**双向默认放行** = 主仓 `forwarding.ts:23-25` 现行语义：
- 平台→游戏：`platformChatTarget` = channel 在绑定表内则放行，否则丢弃 + debug
  （relay.ts:139-148）；
- 游戏→平台：`gameEventChannels(channels)` = channels 原样（全部绑定频道逐频道一帧）。

本仓：`:paper` 组装 `ForwardRules.EMPTY`（恒 true），`PaperRelay` 在绑定判定之外多过一道
`shouldForward*`（接口自述的「二次策略位」）——当前恒放行，行为与主仓一致；配置化
（按频道/事件类型差异化）留未来。

## 6. BindingStore 最小真实现（议题 f）

`:core` 新增 `ConfigBindingStore`（平台无关，进 `core/business`）：

- 构造注入 `PureConfig.channels`（ConfigLoader 已去重保序）；
- `boundChannels()` 返回当前列表（顺序稳定）；`isBound` = contains；
- `addChangeListener(listener)` 收集；`replace(List<String>)` = 整表替换，集合变化时通知
  全部监听并返回 true（对齐主仓 BindingTable.replace 的「变化才推」语义，relay.ts:185-190）；
- 线程：`channels` volatile（reload 未来可能在主线程外触发）；监听 `CopyOnWriteArrayList`。

`:paper` 接线：`bindings.addChangeListener(() -> server.broadcast(
Frames.bindingsUpdated(new BindingsUpdatedBody(bindings.boundChannels()))))`。
**如实写明：变更来源在 STATUS 第 5 条（配置热重载 / reload 命令）落地前为空——监听
注册即接线，当前无人调用 replace，bindings_updated 实际不会触发**（hello_ack 的
channelBindings 与 query kind=bindings 走 Supplier 实时快照，不受影响）。

## 7. 生命周期与配置读取（阶段 1，含议题 g 的范围内部分）

`KuroBridgePurePlugin`：

- **onEnable**：
  1. `getDataFolder().mkdirs()`（目录 = `plugins/KuroBridgePure/`）；
  2. 配置：`config.json` 存在 → `ConfigLoader.load`（**ConfigException → SEVERE 日志后
     抛出中止启用**——错误配置不该静默降级成「无 token 全接口监听」的安全敞口；主仓
     等效行为 = Node 侧配置错误非零退出）；不存在 → `PureConfig.defaults()` + info 日志
     （写明最终生效的 host/port/serverId/channels/token 概览——port 0 = 动态）；
  3. 组装：`ConfigBindingStore`（config.channels）、`ForwardRules.EMPTY`、
     `DirectBusinessScheduler.INSTANCE`（阶段 1 占位，阶段 2 换 Bukkit 实现）、
     `PaperRelay`（BusinessHooks 实现，§2/§3/§4 的载体；onPlatformChat/onCommand 分阶段
     填充）、`SessionContext`（serverId=常量 `kurobridge-pure`（主仓 embedded 亦为常量
     `kurobridge-spike`，配置化随第 5 条）、serverVersion=插件版本、token、
     `bindings::boundChannels`、`ServerTimeouts.defaults()`、`ExecutorTimeoutScheduler`
     （daemon，AutoCloseable）、scheduler、hooks、`PluginKbLogger`（KbLogger → 插件 JUL
     logger 适配，debug→FINE）；
  4. `new PureWsServer(host, port, ctx)` → `startAndWait()` → info 实际端口；
     `relay.attach(server)`（在监听器注册之前完成，无竞争）；
  5. 注册监听器三件（chat/connection/death）+ bindings 变更监听。
- **onDisable**：`server.shutdown()`（既有：已握手对端 close 1001 "server shutdown" +
  stop）；`timeoutScheduler.close()`；引用置 null（volatile 语义上与主仓
  KuroBridgePlugin.onDisable 的清引用法一致）。

监听器三件（主仓逐条镜像）：
- `ChatListener`：`AsyncChatEvent`（异步线程）→ 无 `kurobridge.relay` 权限即静音退出
  （主仓 ChatListener.java:25-36）→ `PlainText.serialize(event.message())` → 逐绑定频道
  `broadcast(Frames.gameChat(...))`。**paper-plugin.yml 补权限声明 `kurobridge.relay`
  （default: true）**；
- `ConnectionListener`：join/quit（主线程）→ 逐频道 join/leave 帧 + `pushStatus`
  补帧（主仓 ConnectionListener.java:29-47）；
- `DeathListener`：`deathMessage() == null → ""`（主仓 DeathListener.java:27-35）→ 逐频道
  death 帧。

fan-out 总则（对齐主仓 relay.ts:300-309）：**每个绑定频道一帧、每帧广播给全部已握手
对端**；无绑定频道或无对端 → 不出帧（debug）。status 无 channel：快照 + 广播单入口（§2）。

## 8. 明确出局项（议题 g）

- **STATUS 第 4 条（真机联调）**：另线负责，本线不做。
- **STATUS 第 5 条（配置落盘 + `/kurobridge reload`）**：不做——首启不生成默认
  config.json，无 reload 命令。但**配置读取**（config.json 存在则加载）在范围内（§7）。
- 热重载引发的 bindings_updated 实际触发、serverId 配置化：随第 5 条。
- 本轮不修改：README.md、docs/AGENTS.md、core/src/test/resources/fixtures/**、
  ProtocolVersions.java、.gitignore（其他块负责）。

## 9. 分阶段提交

| 阶段 | 内容 | 提交 |
|---|---|---|
| 设计书 | 本文件 | `docs: :paper 接线设计书（广播缝隙/线程模型/调度与白名单语义对齐主仓 ADR-027）` |
| 1 | `:core` broadcast + state volatile + 单测；`:paper` 生命周期 + 配置读取 + 三监听器 fan-out + paper-plugin.yml 权限 | `feat: Bukkit 事件 fan-out 接线（chat/join/quit/death/status 经 PureWsServer 广播）` |
| 2 | `BukkitBusinessScheduler` 接入 SessionContext；onPlatformChat 主线程广播进游戏 | `feat: BusinessScheduler 的 Paper 实现（BukkitScheduler 主线程投递）` |
| 3 | `AdminTable` + `CollectingCommandSender` + `VanillaFeedbackCapture` + `PaperWhitelistGateway` + onCommand 全路径 | `feat: 白名单网关与管理员命令判定（dispatchCommand CONSOLE 语义）` |

每阶段全量 build（`mise exec java@25 -- ./gradlew build`）必须绿；STATUS.md 对应条目
随阶段同笔标记完成（注明 2026-09-18）。
