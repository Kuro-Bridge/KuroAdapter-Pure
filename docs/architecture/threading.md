# 线程模型

`kurobridge-ws` 服务端跨三类线程协作:WS 网络线程、定时器线程、业务(主)线程。划分原则:**协议行为在 WS 线程内联完成,业务回调一律经调度位派发**。本文描述每类线程拥有什么、哪些点存在跨线程触达及其防护。

## WS 线程:连接状态所有者

Java-WebSocket 的读写回调(selector/worker 线程)串行触达同一连接的 `PeerSession`,因此握手状态机、空闲计时、收帧分发都在 WS 线程内完成,**不加锁**。

唯一例外:`PeerSession.state` 为 volatile——游戏侧事件 fan-out(见下)从别的线程调用 `established()` / `send()`,需要跨线程只读。

**协议应答在 WS 线程内联完成**(纯协议行为,不经业务调度):`hello_ack`、`pong`、`query_result`、未知帧回执。

## 业务线程:回调派发(`BusinessScheduler`)

`chat` → `onPlatformChat`、`command` → `onCommand` 两类回调包一层 `BusinessScheduler.dispatch`(协议线程 → 业务线程的派发位),因为 Bukkit API 非线程安全:

| 实现 | 用途 | 行为 |
| :--- | :--- | :--- |
| `DirectBusinessScheduler` | `:core` 缺省(测试友好) | 调用线程直执行,无排队无切线程 |
| `BukkitBusinessScheduler`(`:paper`) | 生产 | `Bukkit.getScheduler().runTask` 投递主线程 |

**关停窗口语义**(`BukkitBusinessScheduler`):插件已 disable 时调度必抛 `IllegalPluginAccessException`,实现为 warn + 丢弃——此时 `onDisable` 已先行对对端 close 1001,等待 command 回执的对端由其自身 10s 超时兜底(peer-guide §5.1),服务端无悬挂义务。

**command 的异步结果**:`onCommand` 返回 `CompletableFuture<CommandResultBody>`,完成时经 `whenComplete` 回写 `command_result`——回调线程即 future 完成线程(`:paper` 保证在主线程完成,见 [paper-commands.md](../modules/paper-commands.md))。

## 定时器线程(`TimeoutScheduler`)

hello 超时与空闲检测共用一个定时器注入位:

- 生产实现 `ExecutorTimeoutScheduler`:单线程 `ScheduledExecutorService`,线程 **daemon**、名 `kurobridge-pure-timer`(不阻止 JVM 退出);实现 `AutoCloseable`,`close()` = `shutdownNow()`(`:paper` 在 `onDisable` 调用)。
- 测试用手动推进的假调度器,确定性断言超时行为。
- `IdleTracker` 线程安全:WS 线程(入帧重置)与定时器线程(触发回调)共触,句柄持于 `AtomicReference`(重挂式:每次入帧 cancel + re-schedule)。

## 游戏侧出帧:任意线程的尽力而为

游戏 → 平台的事件推送从业务线程发起,来源两类:`AsyncChatEvent`(异步聊天线程)与 join/quit/death/status(主线程)。二者都落到 `PureWsServer.broadcast`(整帧编码一次,逐会话 `send`),线程安全:

- `PeerSession.state` volatile 保证 `established()` 判定可见;
- `established`/`isOpen` 两查与发送之间的关闭竞态按未送达计(异常 catch,不外抛)——事件推送是尽力而为语义。

详见 [core-server.md](../modules/core-server.md) 的"广播与观测"。
