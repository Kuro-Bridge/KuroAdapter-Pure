# 帧目录

kurobridge-ws v0.4.0 全部消息体(13 个 record,位于 `com.kurobridge.pure.core.protocol.message`),及收发两向的帧型校验。骨架规则见 [wire-format.md](wire-format.md),收帧分发行为见 [session.md](session.md)。

## 方向与形态约定

- **P→S** = 平台对端 → 服务端(本仓为收帧方);**S→P** = 服务端 → 平台对端(出帧方)。
- 形态:请求/响应帧带 `id`(响应与请求同 id);事件帧无 `id` 且 header 只含 `type` 一个键。
- 同一 `type` 可承载异构 body:`chat` 双向共用,按方向区分字段(平台方向是 `sender`,游戏方向是 `playerName`)。
- body 未知键一律忽略(剥离语义);下表"约束"列即第二段校验(`InboundFrames` / `OutboundFrames`),失败抛 `FrameValidationException`。

## 收帧(P→S,`InboundFrames`)

已知 type 集:`hello` / `ping` / `chat` / `command` / `query`。

| type | 形态 | body record | 字段与约束 |
| :--- | :--- | :--- | :--- |
| `hello` | 请求(id) | `HelloBody` | `peerId` / `platform` / `version` 非空;`protocolVersion` 非空且 semver;`token` / `client` 可选字符串(缺失或 null = 无) |
| `ping` | 请求(id) | —(仅 timestamp) | `timestamp` 为 >= 0 的整数(数学整性,见 [wire-format.md](wire-format.md)) |
| `chat` | 事件 | `PlatformChatBody` | `channel` / `sender` / `content` 均非空 |
| `command` | 请求(id) | `CommandBody` + `CommandSource` | `command` 非空(min(1));`source` 为 object 且 `source.channel` / `source.userId` 均非空 |
| `query` | 请求(id) | —(仅 kind) | `kind` 必须为 `status` 或 `bindings` |

易错点:

- **`command.command` 不含前导斜杠是对端调用约定**(peer-guide 文档说明),协议层只做 min(1),`"/list"` 照样通过——两实现(zod / 本仓)刻意对齐,避免跨实现可观察分叉。
- 管理员判定不在协议层:`source` 如实提取,判定发生在服务端业务侧(见 [paper-commands.md](../modules/paper-commands.md))。

## 出帧(S→P,`OutboundFrames` 校验 + `Frames` 工厂构建)

已知 type 集:`hello_ack` / `pong` / `chat` / `join` / `leave` / `death` / `status` / `bindings_updated` / `command_result` / `query_result`。

| type | 形态 | body record | 字段与约束 |
| :--- | :--- | :--- | :--- |
| `hello_ack` | 响应(同 id) | `HelloAckBody.Ok` / `.Error`(sealed) | ok 体:`serverId` / `version` 非空,`protocolVersion` semver,`channelBindings` 非空串数组(可为空数组);error 体:`reason` 非空(配 close 1002/1008) |
| `pong` | 响应(同 id) | —(仅 timestamp) | `timestamp` >= 0 整数,ping 原样回显 |
| `chat` | 事件 | `GameChatBody` | `channel` / `playerName` / `content` 均非空 |
| `join` / `leave` | 事件 | `JoinBody` / `LeaveBody` | `channel` / `playerName` 均非空 |
| `death` | 事件 | `DeathBody` | `channel` / `player` 非空;`message` 只要求是字符串(**允许空串**——deathMessage 为 null 时空串兜底) |
| `status` | 事件 | `StatusBody` | `tps` >= 0 数值(可小数);`onlinePlayers` / `uptimeSeconds` >= 0 整数;**无 channel 字段——全服状态**,不走绑定频道(见下) |
| `bindings_updated` | 事件 | `BindingsUpdatedBody` | `channelBindings` 非空串数组;**变更后完整列表,非增量** |
| `command_result` | 响应(同 id) | `CommandResultBody` | ok 体:`output` 可缺省(字符串数组,元素允许空串,缺失/null 不落字段);error 体:`error` 非空 |
| `query_result` | 响应(同 id) | `QueryResultBody` | ok 体:`data` 任意 JSON(协议层不强校验,形状由请求 kind 决定:`status` → 与 status 事件同构;`bindings` → 字符串数组);error 体:`error` 非空 |

易错点:

- **`death` 的玩家字段名是 `player` 而非 `playerName`**(历史原因,对齐 zod SSOT)。
- `status` 无 channel:服务端事件驱动推送(进退服时附带),无周期上报;`query status` 返回最近一帧缓存,事件稀疏场景可滞后(接受语义,见 [limits.md](../architecture/limits.md))。
- `tps` 序列化:数学上整数的 double 落为整数(对齐 TS `JSON.stringify`),见 [wire-format.md](wire-format.md)。

## 未知帧回执

对未知 type 的容忍策略在 [session.md](session.md);其中请求型未知帧的回执由 `Frames.unknownFrameResult` 构造:同 id 的 `<type>_result`,body `{ok:false, error:"unknown frame type"}`(文案为协议契约固定值 `Frames.UNKNOWN_FRAME_TYPE`)。

## 契约测试口径

上表即金样本门禁的逐帧对表依据:`InboundFrames` / `OutboundFrames` 消费 `dir=peer->server` / `dir=server->peer` 夹具;出帧工厂 `Frames` 构造服务端全部回帧(类型安全,body 显式构建不依赖反射)。金样本机制见 [testing.md](../development/testing.md)。
