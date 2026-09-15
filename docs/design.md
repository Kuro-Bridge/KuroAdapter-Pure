# KuroAdapter-Pure 设计书

> 纯净线第一站：**纯 Java Paper 插件**，实现 `kurobridge-ws` v0.4.0 协议的 WS 服务端。
> 零 Node、零子进程、零 IPC、零嵌入式胶水——与主仓（KuroAdapter，Paper JAR + Node 子进程）
> 相同的协议行为，纯 Java 单体实现。
>
> 协议契约的实现依据 = **KuroProtocol 仓**（`C:\Dev\MC-Ecosystem\KuroProtocol`）的
> `docs/peer-guide.md` + `src/frame.ts` / `src/messages/ws.ts` / `src/meta.ts`（SSOT），
> 一致性门禁 = 该仓 `fixtures/v0.4/` 金样本（整目录拷贝进本仓测试资源消费，见 §7）。

## 0. 定位与硬性红线

1. **零胶水**：无 Node、无子进程、无 stdin/stdout IPC、无嵌入式打包。运行时依赖白名单
   见 §6，白名单之外一律不引入。
2. **主仓只读**：KuroAdapter 仓只可读取参考（协议行为基准 `bridge/core/src/server.ts`），
   不得写入；姊妹仓 KuroProtocol 只读拷贝 fixtures。
3. **协议 SSOT 在 KuroProtocol**：本仓 `:core` 内的协议常量（`ProtocolVersions`）是**人工
   同步的硬编码副本**，协议 bump 时须随 KuroProtocol 同批更新并重新 pin fixtures。
4. Java 工具链 25、字节码 release 21（对齐主仓 ADR-015）、`-Xlint:all -Werror`、UTF-8。

## 1. 分层

```
:core   com.kurobridge.pure.core      协议层 + 业务骨架；零 Bukkit API，可独立 JUnit 测试
:paper  com.kurobridge.pure(.paper)   Paper 薄适配：插件生命周期 + （下一阶段）Bukkit 事件接线
```

- `:core` 依赖：Jackson（JSON）、Java-WebSocket（WS 服务端）。**不含任何 Paper API**。
- `:paper` 依赖 `:core`（implementation）+ Paper API（compileOnly）；产物 shadowJar fat JAR。
- 多模块预留：`settings.gradle.kts` 注释说明未来可扩展 `fabric` / `velocity` 模块
  （复用 `:core`，只换适配层——与主仓 ADR-021 多版本策略同构）。

## 2. :core 协议层类结构

```
com.kurobridge.pure.core
├── protocol
│   ├── ProtocolVersions   常量：PROTOCOL_VERSION="0.4.0"、WS_SUBPROTOCOL="kurobridge-ws.v1"
│   │                      （硬编码副本；SSOT = KuroProtocol src/meta.ts，bump 须人工同步）
│   ├── VersionCompat      版本协商：主版本号相同即兼容；任一侧格式非法 = 不兼容
│   ├── Frame              帧模型 record(type, id, body)：id 可空（事件帧无 id）
│   ├── FrameCodec         Jackson 编解码：parseWire（骨架）/ encode（单行 JSON）
│   ├── Frames             出帧工厂：helloAckOk/Error、pong、gameChat、join、leave、death、
│   │                      status、bindingsUpdated、commandResult、queryResult
│   ├── InboundFrames      收帧集（peer→server）：hello/ping/chat/command/query 的具体校验
│   ├── OutboundFrames     出帧集（server→peer）：10 种出帧的具体校验（金样本门禁用）
│   ├── ProtocolException  类型化错误基类（unchecked）
│   ├── WireFormatException       两段式第一段（线格式骨架）拒绝
│   ├── FrameValidationException  两段式第二段（具体帧型）拒绝
│   └── message            body record 集（见 §2.2）
├── server
│   ├── KbLogger           日志抽象（debug/info/warn/error，注入；默认 JUL 实现）
│   ├── TimeoutScheduler   一次性定时器抽象 schedule(ms, task)→Cancellable（注入）
│   ├── ExecutorTimeoutScheduler   生产实现（ScheduledExecutorService，daemon 线程）
│   ├── IdleTracker        空闲检测：任意入帧重置，阈值内无入帧触发 close；0=禁用
│   ├── BusinessScheduler  业务回调调度抽象 dispatch(Runnable)（默认直执行实现）
│   ├── ServerTimeouts     record(helloTimeoutMs=10000, idleTimeoutMs=30000)；0=禁用
│   ├── SessionContext     会话环境：serverId/version/token/绑定快照供应者/hooks/调度器
│   ├── BusinessHooks      业务回调集：onPlatformChat / onCommand / latestStatus / channelBindings
│   ├── WsConnection       传输抽象：send(text) / close(code, reason) / isOpen()
│   ├── PeerSession        每连接握手状态机 + 两段式收帧分发 + 未知帧容忍（§2.3）
│   └── PureWsServer       Java-WebSocket 封装（§2.4）
└── business
    ├── PureConfig / ConfigLoader / ConfigException   配置形状与加载（§4）
    ├── BindingStore       频道绑定集合：fan-out 迭代 + 变更通知位（接口 + 空实现）
    ├── ForwardRules       转发判定（接口 + 空实现）
    └── WhitelistGateway   白名单网关接口（语义走 MC 原生 whitelist 命令）+ 空实现
```

### 2.1 帧格式与两段式解析

线格式：WS 文本帧一帧一行 JSON `{"header":{"type":"...","id":"<uuid>"},"body":{}}`。

- **第一段（wire 骨架，`FrameCodec.parseWire`）**：根为 object、`header` 为 object、
  `header.type` 匹配 `^[a-z][a-z0-9_]*$`、`header.id` 存在时必须 canonical UUID
  （`8-4-4-4-12` 十六进制；Java `UUID.fromString` 接受无横线串，故用正则对齐 zod `z.uuid()`）。
  顶层/header 的未知字段不报错（对齐 zod 非严格 object 的剥离语义）。
- **第二段（dispatch，`InboundFrames` / `OutboundFrames`）**：按 type 分发到具体帧型校验——
  请求/响应帧 id 必填（UUID 已由第一段保证）；**事件帧 header 必须只含 `type` 一个键**
  （对齐 zod `strictObject`：携带 id 或任何多余键即拒）。body 未知字段剥离不报错。

已知收帧 type 集（对齐 KuroProtocol `WS_INBOUND_TYPES`）：`hello / ping / chat / command / query`。

### 2.2 帧 body 一览（校验规则）

方向与字段以 KuroProtocol `src/messages/ws.ts` 为 SSOT：

| 帧 | 方向 | 形态 | body 校验 |
|---|---|---|---|
| hello | P→S | 请求(id) | peerId/platform/version 非空；protocolVersion `^\d+\.\d+\.\d+$`；token/client 可选 |
| ping | P→S | 请求(id) | timestamp 整数 ≥0 |
| chat | P→S | 事件 | channel/sender/content 均非空 |
| command | P→S | 请求(id) | command 非空且**不含前导斜杠**；source.{channel,userId} 均非空 |
| query | P→S | 请求(id) | kind ∈ {status, bindings} |
| hello_ack | S→P | 响应(同 id) | ok 体 {serverId,version 非空, protocolVersion 三元组, channelBindings: 非空串数组}；error 体 {reason 非空} |
| pong | S→P | 响应(同 id) | timestamp 整数 ≥0 |
| chat | S→P | 事件 | channel/playerName/content 均非空（同 type 不同 body，按方向区分） |
| join / leave | S→P | 事件 | channel/playerName 均非空 |
| death | S→P | 事件 | channel/player 非空；**message 允许空串**（字段名是 `player` 不是 `playerName`） |
| status | S→P | 事件 | tps ≥0（数值）；onlinePlayers/uptimeSeconds 整数 ≥0；无 channel |
| bindings_updated | S→P | 事件 | channelBindings: 非空串数组（完整列表非增量） |
| command_result | S→P | 响应(同 id) | ok 体 {output?: string[]}（output 可缺省）；error 体 {error 非空} |
| query_result | S→P | 响应(同 id) | ok 体 {data: 任意}；error 体 {error 非空} |

数值语义对齐 zod：`z.number().int()` 接受数学上整数的 `1.0`（Jackson 用 `canConvertToLong`
判定），`tps` 为任意非负数值。

> 注：主仓 zod 对 command 只强制 `min(1)`，「不含前导斜杠」在 peer-guide 中是对端约定；
> 本仓按任务契约**收紧为拒绝前导斜杠**（合格对端本就不会发 `/cmd`，收紧不影响互操作）。

### 2.3 握手状态机与收帧分发（PeerSession）

每连接一个 `PeerSession`，状态 `AWAITING_HELLO → ESTABLISHED → CLOSED`：

1. **hello 超时**：连接建立即武装 10s（`ServerTimeouts.helloTimeoutMs`，0=禁用）；超时
   → close 1002 "hello timeout"。握手成功/被拒即卸下。
2. **空闲检测（IdleTracker）**：缺省 30s（0=禁用）；**任何入帧（含校验失败帧）重置计时**；
   超时 → close 1001 "idle timeout"。
3. **收帧分发**（对齐主仓 `bridge/core/src/server.ts` 的两段式 + 容忍语义）：
   - JSON 解析失败 → warn 丢弃；
   - wire 骨架失败 → warn 丢弃不断连（仍重置空闲计时）；
   - `hello`：校验通过 → 版本协商 → 鉴权 → 回 ack；校验失败 → **不回执**，warn 丢弃
     （连接等 10s hello 超时）；重复 hello → warn 忽略；
   - 已知 type（ping/chat/command/query）且未握手 → warn 丢弃；
   - 已知 type 且已握手 → 具体校验：ping 同 id 回 pong（timestamp 回显）；chat 经
     BusinessScheduler 派发 onPlatformChat；command 经 BusinessScheduler 派发
     onCommand（无 handler → `command_result {ok:false, error:"command handler not available"}`）；
     query 本地作答（status 用 latestStatus 缓存，无缓存回 `no status yet`；bindings 用
     绑定快照供应者）；
   - 已知 type 具体校验失败 → warn 丢弃不回执不断连；
   - **未知帧容忍（永不断连、无阈值计数）**：未知 type 带 id 且不以 `_result` 结尾 → 回
     同 id `<type>_result` `{ok:false, error:"unknown frame type"}`；未知 type 以 `_result`
     结尾 → 忽略不回执（防乒乓）；未知 type 无 id（事件）→ 忽略 + debug。
     容忍在握手前同样生效。
4. **版本协商（先于鉴权）**：`VersionCompat.isCompatible(peer, server)` 主版本相同即兼容；
   不兼容 → hello_ack `{ok:false, reason:"protocol version mismatch: peer=<对端版本>"}` +
   close 1002（close reason 同文本）。任一侧格式非法 = 不兼容（hello body 已强制格式，
   属防御性兜底）。
5. **鉴权（后于版本协商）**：服务端 token 非空时 hello 缺 token 或不符 → hello_ack
   `{ok:false, reason:"auth failed"}` + close 1008（close reason "auth failed"）。
   token 空 = 不鉴权。
6. **关服**：已握手对端 close 1001 "server shutdown"。

### 2.4 PureWsServer（Java-WebSocket 封装）

- **子协议协商**：升级请求的 `Sec-WebSocket-Protocol` 列表**包含** `kurobridge-ws.v1`
  即接受并回显（对齐主仓 ws 库 `handleProtocols: protocols.has(...) ? ... : false` 的
  语义）；缺失/不符 → 在 `onWebsocketHandshakeReceivedAsServer` 抛 `InvalidDataException(400)`
  拒绝升级（HTTP 400），不进协议层。实现上同时以 `Draft_6455(extensions, [Protocol(子协议)])`
  构造，使响应头回显选中子协议。
- **动态端口**：`port = 0` 时 `listen(0)` 由 OS 分配；`startAndWait()` 阻塞至绑定完成并
  返回实际端口。
- **文本帧入口**：每连接适配为 `WsConnection` + `PeerSession`；二进制帧忽略（warn）。
- **close 封装**：`shutdown()` 先对全部会话 close 1001 "server shutdown"，再停服务端。

## 3. 线程模型

- **WS 线程拥有连接状态**：Java-WebSocket 的读写回调（selector/worker 线程）串行触达
  同一连接的 `PeerSession`，握手状态机、空闲计时、收帧分发都在 WS 线程内完成，不加锁。
- **协议应答在 WS 线程内联完成**：pong / query_result / hello_ack / 未知帧回执等纯协议
  行为立即回写，不经业务调度。
- **业务回调经 BusinessScheduler 派发**：chat → onPlatformChat、command → onCommand 都
  包一层 `BusinessScheduler.dispatch`；缺省 `DirectBusinessScheduler` 直执行（测试友好），
  **下一阶段 Paper 侧实现投递 Bukkit 主线程**（Bukkit API 非线程安全）。
- **定时器经 TimeoutScheduler 注入**：生产实现 `ExecutorTimeoutScheduler`（daemon
  ScheduledExecutorService，不阻止 JVM 退出）；测试用手动推进的假调度器（确定性断言
  超时行为，等效主仓 ManualScheduler + ManualClock 的时钟注入法）。
- command 的异步结果（`CompletableFuture`）完成时回写 command_result——回调线程即
  future 完成线程（Paper 侧将保证在主线程完成）。

## 4. 业务骨架（本阶段：接口 + 空实现）

- **PureConfig + ConfigLoader（Jackson）**：形状对齐主仓 `docs/config-schema.md` 但
  **去掉 runtime 与 embedded 段**（纯 Java 线无子进程/无嵌入，两段无意义）：
  `channels: string[]`（必填可空，去重保序）、`token: string`（缺省 ""）、
  `admins: [{channel, users: string[]}]`（缺省 []，channel 与 users 各自去重保序）、
  `ws: {host?, port 1-65535}`（整段缺省 = 动态端口全接口；只配 host 合法）。
  行为：多余字段剥离、缺字段补缺省、非法值抛类型化 `ConfigException`
  （测试矩阵对齐主仓 `bridge/core/src/business/__tests__/config.test.ts`，剔除两段后）。
- **BindingStore**：频道绑定集合——`boundChannels()` fan-out 迭代、`isBound(channel)`
  入站过滤、`addListener` 变更通知位（bindings_updated 推送用）。空实现返回空集。
- **ForwardRules**：转发判定（平台→游戏 / 游戏→平台）。空实现一律放行（`// 下一阶段`）。
- **WhitelistGateway**：白名单网关接口——语义上走 MC 原生 `whitelist` 命令（SSOT 是
  `whitelist.json`，本仓不自建白名单存储）。空实现无操作（`// 下一阶段`）。

## 5. 与主仓 platforms/je 的差异

| 维度 | 主仓 platforms/je | 本仓 |
|---|---|---|
| 架构 | Paper JAR + Node 子进程（业务在 TS bridge/core），stdin/stdout JSON-lines IPC | 纯 Java 单体，业务与协议同 JVM |
| :core 职责 | IPC 客户端 / 子进程管理 / JSON-lines 解析 | kurobridge-ws 协议服务端 + 业务骨架 |
| WS 服务端 | Node `ws` 库（bridge/embedded 引导层） | Java-WebSocket（:core 内 PureWsServer） |
| 嵌入式 | JAR 内嵌产物 + embed 工具链 | 无（external 对端直连） |
| 配置 | 含 runtime.autoRestart / embedded.napuketto 段 | 去掉两段，其余形状对齐 |
| 协议 SSOT | 仓内 bridge/protocol（zod） | KuroProtocol 仓（协议常量为人工同步副本） |

保留不变：Gradle 多模块形态（`:core` 零平台 API + `:paper` 适配）、group `com.kurobridge`、
工具链 25 / release 21 / `-Werror`、Spotless(palantir 2.71.0)、shadow fat JAR 产物命名。

## 6. 依赖白名单与理由

| 依赖 | 范围 | 理由 |
|---|---|---|
| `io.papermc.paper:paper-api` | :paper compileOnly | Paper 插件 API，运行期由服务端提供 |
| `com.fasterxml.jackson.core:jackson-databind:2.18.0` | :core | JSON 编解码（帧解析 + 配置加载）；主仓 Java 侧同款同版本 |
| `org.java-websocket:Java-WebSocket:1.6.0` | :core | WS 服务端实现；**MIT 许可**（与本项目 MIT 兼容）、零传递运行时（slf4j-api 由 Paper 运行期自带，shadowJar 排除） |
| `org.junit:junit-bom:5.11.0` + junit-jupiter + launcher | test | JUnit 5（主仓同款） |
| `com.diffplug.spotless` 7.0.2 / `com.gradleup.shadow` 9.0.0 | 构建插件 | 格式化 / fat JAR（主仓同款） |

白名单之外零引入。Java-WebSocket 的 `Protocol`/`Draft_6455` 提供子协议协商；
不引入 Netty/tyrus 等更重方案。

## 7. 测试策略

1. **自有单测**（`:core`）：FrameCodec 往返/骨架拒绝/未知字段剥离；VersionCompat 兼容
   矩阵；PeerSession（fake WsConnection + 手动调度器）：握手成功 ack 内容 / 版本拒绝
   （reason 精确断言）/ 鉴权拒绝 / hello 超时 / 空闲超时（含非法帧重置）/ 重复 hello /
   握手前丢弃 / pong 回显 / 未知帧三分支 / query 两 kind / command 未注册 handler；
   ConfigLoader 矩阵；PureWsServer 轻量真机自测（动态端口 + 子协议接受/拒绝 + 回环握手）。
2. **金样本夹具（两仓一致性门禁）**：KuroProtocol `fixtures/v0.4/` 整目录拷贝至
   `core/src/test/resources/fixtures/v0.4/`（16 份 JSON），`core/src/test/resources/
   fixtures/PIN.md` 记录来源仓/协议版本/拷贝日期。`FixtureConformanceTest` 动态发现并
   逐份消费：`schema=accept` 每帧按方向过 wire + 具体校验；`schema=reject+stage=wire`
   骨架必拒；`reject+stage=dispatch` 骨架过、具体校验必拒；握手三份额外跑 PeerSession
   行为断言（回执帧语义等价 + close code/reason 精确）。

## 8. 实现顺序

1. gradle 骨架（多模块 + wrapper + spotless/shadow 约定）；
2. `:core` 协议层（Frame/Codec → 两段式校验 → 版本协商 → PeerSession 状态机 →
   PureWsServer），随实现补单测；
3. 业务骨架（ConfigLoader + 三个接口空实现）+ 配置矩阵测试；
4. 金样本夹具接线（拷贝 + PIN + 门禁测试）；
5. `:paper` 插件壳（paper-plugin.yml + 主类空壳，`// 下一阶段` 标注）。
