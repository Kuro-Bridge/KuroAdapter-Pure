# PureWsServer:WS 服务端机制

`:core` 的 `com.kurobridge.pure.core.server` 包承载 WS 服务端与连接管理。核心类 `PureWsServer` 继承 Java-WebSocket(1.6.0)的 `WebSocketServer`;kurobridge 永远是 WS 服务端角色,对端(平台侧)主动连入。

## 启动与子协议门禁

- 构造:`new PureWsServer(host, port, context)`。`host` 为 null = 绑定全部接口(`0.0.0.0`);`port` 为 0 = OS 动态端口。Draft 携带子协议 `kurobridge-ws.v1`(`Draft_6455` + `Protocol`)。
- **子协议门禁**(`onWebsocketHandshakeReceivedAsServer`):升级请求的 `Sec-WebSocket-Protocol` 头按逗号切分后须**包含** `kurobridge-ws.v1`(对齐主仓 ws 库 `handleProtocols` 语义);缺失/不符 → 抛 `InvalidDataException(400)` 拒绝升级,不进协议层。通过则返回 101,子协议回显由 Draft 完成。

## 绑定等待(`startAndWait`)

`start()` 后以 200ms 间隔轮询绑定结果,返回实际端口(port=0 时为 OS 分配值):

- **绑定期失败即时暴露**:`onError` 收到无连接上下文的异常(conn == null)即绑定期失败(Java-WebSocket 的 bind 异常走 `handleFatal(null)`,`onStart` 永不回调),轮询发现后抛 `IllegalStateException`(携带底层 `BindException`)——调用方(`:paper` 插件)不让端口被占空等 10s;
- 仍保留 10s 兜底超时,防 selector 线程无响应的极端情况。

`:paper` 侧对绑定失败的处理(不回退动态端口)见 [paper-plugin.md](paper-plugin.md)。

## 连接生命周期回调

| 回调 | 行为 |
| :--- | :--- |
| `onOpen` | 创建 `PeerSession`(经 `WsConnection` 适配)存入 `ConcurrentHashMap` 并 `start()`(武装两个计时器) |
| `onMessage(String)` | 转交对应 `PeerSession.onMessage` |
| `onMessage(ByteBuffer)` | **二进制帧丢弃**:warn 不断连,与非法帧同策略(协议仅用文本帧) |
| `onClose` | 从会话表移除并落定状态、清理计时器 |
| `onError` | conn == null 视为绑定期失败(见上);其余记错误日志 |
| `onStart` | 记录实际绑定端口,放行 `startAndWait` |

`WsConnection` 是传输抽象接口(`send` / `close` / `isOpen`):`:core` 只依赖此接口,测试注入 fake,生产由内部 `JavaWebSocketConnection` 适配;send 抛错视为连接已死,由 `onClose` 收尾。

## 广播与观测

- `broadcast(Frame)`:整帧**编码一次**后发给全部已握手对端,返回送达数(对齐主仓 `sendToEstablished` 契约)。可从任意线程调用(游戏侧事件 fan-out 会来自主线程/异步聊天线程);`established`/`isOpen` 两查与发送之间的关闭竞态按未送达计(send 抛错 catch 不外抛)——事件推送是尽力而为语义;0 送达时 debug 记录。
- `establishedCount()`:已握手对端数,观测用。

## 关服(`shutdown`)

顺序:逐会话 close 1001 `server shutdown` → 清空会话表 → `stop(1000)` 停服务端。

## 会话装配(`SessionContext`)

`SessionContext` 是每服务端一份的依赖注入包,全部连接共享:

| 字段 | 内容 | 备注 |
| :--- | :--- | :--- |
| `serverId` | `hello_ack` 上报的服务器标识 | 来自配置 `server.id`,进程固定 |
| `serverVersion` | 服务端版本号 | `:paper` 取插件版本 |
| `token` | 鉴权令牌 | 进程生命周期固定;空 = 不鉴权 |
| `channelBindings` | `Supplier<List<String>>` 绑定快照供应者 | query bindings 实时作答、hello_ack 快照 |
| `timeouts` | `ServerTimeouts`(hello 10s / idle 30s,0=禁用) | |
| `scheduler` | `TimeoutScheduler` 定时器注入 | |
| `businessScheduler` | `BusinessScheduler` 业务派发位 | 见 [threading.md](../architecture/threading.md) |
| `hooks` | `BusinessHooks` 业务回调集 | `onPlatformChat` / `onCommand` / `latestStatus` |
| `logger` | `KbLogger` 日志注入 | `:core` 不绑定具体日志实现 |

配套接口:`TimeoutScheduler`(一次性定时器 + 幂等 `Cancellable`)、`BusinessHooks`(协议层 → 业务层的事件入口;回调在 BusinessScheduler 派发的线程上执行)、`KbLogger`(debug/info/warn/error)。

协议行为(握手、收帧分发、超时)见 [session.md](../protocol/session.md)。
