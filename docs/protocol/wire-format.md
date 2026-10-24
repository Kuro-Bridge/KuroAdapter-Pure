# 线上格式与版本兼容

kurobridge-ws v0.4.0 的帧在线上是一条 WebSocket **文本帧**,内容为单个 JSON object:

```json
{"header": {"type": "chat", "id": "…"}, "body": {}}
```

- 请求/响应帧携带 `id`(canonical UUID);事件帧不携带。
- 二进制帧直接丢弃(warn,不断连),见 `PureWsServer`([core-server.md](../modules/core-server.md))。
- 代码即事实:本文描述均对应 `com.kurobridge.pure.core.protocol` 包;协议 SSOT 在姊妹仓 KuroProtocol(`src/meta.ts` / `src/frame.ts` / `src/messages/ws.ts`),本仓常量是人工同步副本(见文末)。

## 两段式解析

收帧解析分两段,拒绝行为不同,异常类型也不同:

| 段 | 载体 | 校验内容 | 失败异常 | 行为 |
| :--- | :--- | :--- | :--- | :--- |
| 第一段(wire 骨架) | `FrameCodec.parseWire` | 见下 | `WireFormatException` | warn 丢弃,不断连 |
| 第二段(具体帧型) | `InboundFrames` / `OutboundFrames` | 按 type 分发的 body 校验、id/事件 header 断言 | `FrameValidationException` | warn 丢弃,不回执,不断连 |

两者均为 unchecked `ProtocolException` 子类,在收帧分发处(`PeerSession`)统一捕获,不外溢。逐条行为见 [session.md](session.md)。

### 第一段:wire 骨架(`FrameCodec.parseWire`)

按序校验,任一失败即抛 `WireFormatException`:

1. 整体必须是合法 JSON,且根为 object;
2. `header` 必填且为 object;
3. `header.type` 必填、字符串、匹配 `^[a-z][a-z0-9_]*$`(全小写 snake_case,`Frame.TYPE_PATTERN`);
4. `header.id` 可选;出现时(`hasNonNull`)必须为字符串且匹配 canonical UUID(`Frame.UUID_PATTERN`,8-4-4-4-12 十六进制)。用正则而非 `UUID.fromString`,因为后者接受无横线串,会与 zod `z.uuid()` 产生分叉;
5. `body` 缺失时补为 JSON null(必然被第二段拒绝)。

骨架层**容忍未知字段**:根、header、body 上的多余键不报错,对齐 zod 非严格 object 的剥离语义(实现上"只取已知键")。`Frame` record 原样保留 header(事件帧的严格校验需要检视其键集),但出帧编码只落 `type`/`id`,header 未知字段不透传。

### 第二段:具体帧型校验

- 请求/响应帧:`Frame.requireRequestId()`——`id` 必填(UUID 格式已由第一段保证)。
- 事件帧:`Frame.requireEventHeader()`——禁止携带 `id`,且 header **只允许 `type` 一个键**(对齐 zod `strictObject`,多余键即拒)。
- body 校验按 type 分发到对应 record,完整目录见 [messages.md](messages.md);body 未知键一律忽略。

字段级校验由包内助手 `JsonValidations` 提供,语义逐条对齐 zod,值得注意的两处:

- **数学整性**:`z.number().int()` 接受数学上整数的 `1.0`、拒绝 `1.5`。Jackson 的 `canConvertToLong` 对浮点恒真不可用,故自实现 `isIntegral`:整数节点直接合格;浮点节点须有限且无小数部分(对齐 JS `Number.isInteger`)。用于 `ping.timestamp`、`status.onlinePlayers` 等。
- **semver**:`protocolVersion` 须匹配 `^\d+\.\d+\.\d+$`。

## 出帧编码(`FrameCodec.encode`)

- 单行 JSON 文本,键序 header(`type` → `id`,id 非 null 才落)→ `body` 原样。
- `tps` 经 `Frames.tpsNode` 处理:数学上整数的 double 序列化为整数,对齐 TS 侧 `JSON.stringify(20) === "20"` 的可观察行为。
- `command_result` ok 分支仅当 `output` 非 null 才写 `output` 字段;error 分支写 `error`。

## 协议版本与兼容策略

常量集中于 `ProtocolVersions`:

| 常量 | 值 | 语义 |
| :--- | :--- | :--- |
| `PROTOCOL_VERSION` | `0.4.0` | hello 期协商用;当前版本 = 品牌迁移 kurobot-ws → kurobridge-ws,帧形状零变化 |
| `WS_SUBPROTOCOL` | `kurobridge-ws.v1` | WS 子协议(大版本门禁):升级握手缺失/不符即 HTTP 400 拒绝,不进协议层(机制见 [core-server.md](../modules/core-server.md)) |

兼容判定 `VersionCompat.isCompatible(peer, server)`:两侧都解析出**主版本号**且相等即兼容,次/补丁位自由浮动;任一侧不满足 `主.次.补丁` 三元组格式即不兼容(主版本号超 int 上限按不兼容处理,防御性兜底)。

对端(如 v0.2.x / v0.3.x)主版本相同时可握手成功,`hello_ack` 回显服务端实际版本。

## SSOT 同步义务

`ProtocolVersions` 是**人工同步的硬编码副本**,权威在 KuroProtocol 仓 `src/meta.ts`。协议 bump 时三件事同批落地:更新本副本 → 重新拉取金样本 → 更新 PIN 记录;机械流程见 [testing.md](../development/testing.md) 的"金样本刷新"。
