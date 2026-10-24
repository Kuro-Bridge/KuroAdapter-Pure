# 会话与握手

单个对端连接的生命周期由 `PeerSession` 状态机管理(每连接一个实例),行为对齐主仓 `bridge/core/src/server.ts`。本文描述状态机、握手流程、超时与各类收帧的处理口径;线程归属见 [threading.md](../architecture/threading.md)。

## 状态机与关闭码

状态:`AWAITING_HELLO → ESTABLISHED(→ CLOSED)`。`state` 为 volatile 供跨线程只读(fan-out 出口),其余连接状态 WS 线程独占。

| 关闭码 | 常量 | 触发场景 | close reason |
| :--- | :--- | :--- | :--- |
| 1002 | `CLOSE_PROTOCOL_ERROR` | 版本不匹配、hello 等待超时 | `protocol version mismatch: peer=<版本>` / `hello timeout` |
| 1001 | `CLOSE_GOING_AWAY` | 空闲超时、服务端正常关服 | `idle timeout` / `server shutdown` |
| 1008 | `CLOSE_POLICY_VIOLATION` | token 鉴权失败 | `auth failed` |

## 超时(`ServerTimeouts` + `IdleTracker`)

| 检测 | 默认阈值 | 机制 |
| :--- | :--- | :--- |
| hello 等待 | 10 000 ms | 连接建立即武装(`PeerSession.start()`);握手成功或被拒即卸下;超时 close 1002 `hello timeout` |
| 空闲检测 | 30 000 ms | 重挂式计时:**任何入帧都重置**(含校验失败帧);超时 close 1001 `idle timeout` |

两者阈值可配(`ServerTimeouts` record),**0 = 禁用**对应检测。定时器经 `TimeoutScheduler` 注入,生产实现为 daemon 线程池([threading.md](../architecture/threading.md));无协议层主动 ping、无库级心跳,活性兜底即此空闲计时。

## 收帧分发(`PeerSession.onMessage`)

按序判定:

1. **先重置空闲计时**(无论后续结果);
2. 骨架校验失败(`WireFormatException`)→ warn 丢弃(日志预览截断 200 字符),不断连;
3. `hello` → 第二段校验,进入握手流程(下节);校验失败 → **不回执**,warn 丢弃,连接保持到 hello 超时(协议契约);
4. 未知 type → 未知帧容忍(下节);**此判定先于握手状态检查,握手前同样生效**;
5. 已知业务帧(ping/chat/command/query)但未握手 → warn 丢弃;
6. 已握手 → 具体校验与分发(下表);校验失败 → warn 丢弃,不回执,不断连。

## 握手流程(`handleHello`)

顺序固定:**版本协商先于鉴权**。

1. 重复 hello(已 ESTABLISHED)→ warn 忽略;
2. `VersionCompat.isCompatible(peer, server)` 不通过 → `hello_ack {ok:false, reason:"protocol version mismatch: peer=<对端版本>"}` + close 1002(回执与 close reason 同文案);
3. token 校验:服务端 token 非空且与 `hello.token` 不等 → `hello_ack {ok:false, reason:"auth failed"}` + close 1008;**服务端 token 为空 = 不鉴权,全放行**;
4. 通过 → 置 ESTABLISHED、记录 peerId、卸下 hello 计时,回 `hello_ack ok` 体(serverId / serverVersion / protocolVersion / channelBindings 绑定快照)。

握手被拒时同时取消 hello 计时与空闲计时,连接随即关闭。`serverVersion` 来源为插件版本([paper-plugin.md](../modules/paper-plugin.md));`serverId` 来自配置(见 [core-config.md](../modules/core-config.md))。

## 已握手帧口径

| type | 处理 |
| :--- | :--- |
| `ping` | 同 id 回 `pong`,`timestamp` 原样回显(WS 线程内联) |
| `chat` | 经 `BusinessScheduler` 派发 `onPlatformChat`(主线程广播,见 [paper-plugin.md](../modules/paper-plugin.md)) |
| `command` | 经 `BusinessScheduler` 派发;同 id 回 `command_result`。细节:onCommand 抛异常 → failure(异常消息);未注册 handler → `failure("command handler not available")`;`CompletableFuture` 完成时回帧(失败回 `getMessage()`,无消息回异常类名) |
| `query` | **协议层本地作答**,不经业务回调:`kind=status` 用最近 status 快照,无快照回 `failure("no status yet")`;`kind=bindings` 用绑定表实时快照 |

## 未知帧容忍

永不断连、无阈值计数,三分支:

| 形态 | 处理 |
| :--- | :--- |
| 无 id(事件帧) | 忽略 + debug |
| 有 id 且 type 以 `_result` 结尾 | 忽略不回执(**防乒乓循环**——响应不再触发回执) |
| 其余(未知请求帧) | 回同 id `<type>_result` `{ok:false, error:"unknown frame type"}` |

## 连接关闭与关服

- 对端断开(`onClose`):落定 CLOSED、清理两个计时器、按是否已握手分级记日志。
- 服务端关服:逐会话 close 1001 `server shutdown`(`PureWsServer.shutdown()` 的完整停机顺序见 [core-server.md](../modules/core-server.md))。
