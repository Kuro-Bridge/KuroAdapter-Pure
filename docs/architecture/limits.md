# 已知边界与显式非目标

本文是行为边界与有意取舍的唯一清单,逐条附代码依据。改掉任何一条前,先更新本文。

## 行为边界(接受语义,非缺陷)

| 边界 | 说明 | 依据 |
| :--- | :--- | :--- |
| `query status` 可滞后 | 返回最近一帧缓存;status 为事件驱动推送(当前仅进退服时附带),事件稀疏场景可滞后。协议文本允许;周期刷新已被否决。全新起服无事件时正确回 `no status yet` | `PeerSession.answerQuery` + `PaperRelay.pushStatus`(快照与推送同源) |
| `version` 命令仅捕获同步首行输出 | vanilla 命令回退 console 执行后,反馈经 log4j appender 收集;只收 `Server thread` 在 attach/detach 窗口内的日志,异步余量直落控制台、不进回执 | `VanillaFeedbackCapture`(仅 `Server thread` + 窗口)、`PaperCommandDispatcher` |
| 无帧大小上限 | 未配置消息长度上限,超长帧依赖对端自律 | `PureWsServer` 无 `setMaxTextMessageSize` 等配置 |
| 无库级心跳 | 服务端不发协议层 ping、不判 pong 超时,未调 Java-WebSocket 的 `setConnectionLostTimeout`;活性兜底 = 应用层空闲计时(任意入帧重置,默认 30s) | `PureWsServer` / `IdleTracker` / `ServerTimeouts` |
| `PeerSession.helloTimer` 跨线程良性竞态 | 计时器句柄仅 WS 线程持有,但定时器回调在调度线程触发;`Cancellable.cancel` 幂等,竞争结果仅为多余的空操作,判定为良性既有边界,未修 | `PeerSession` 定时器字段注释 |
| 关停窗口任务丢弃 | 插件 disable 后业务回调调度必抛,处理为 warn + 丢弃;等待 command 回执的对端由其自身 10s 超时兜底,服务端无悬挂义务 | `BukkitBusinessScheduler.dispatch` |
| 白名单网关无调用方 | `PaperWhitelistGateway` 结构化 API(list/add/remove)当前无调用方,command 帧直传 `whitelist ...` 字符串已达同效 | `PaperWhitelistGateway` / [paper-commands.md](../modules/paper-commands.md) |
| plain 序列化器 deprecation | paper-api 1.21.4 只暴露 `PlainComponentSerializer.plain()`(adventure 4.15 起 deprecated),局部压制使用;升级后换 plainText() | `PlainText` |

## 有意取舍(设计决策,勿当缺陷修)

- **空 token 拒绝启动监听**:对主仓"WARN 不阻断"的有意安全偏差(fail-fast 门禁,见 [core-config.md](../modules/core-config.md))。
- **无命令白名单**:管理员(`admins` 命中)即可执行任意命令,不设二次白名单。
- **端口绑定失败不回退动态端口**:回退会静默破坏对端静态 url,SEVERE 指路改 `ws.port` 后重启。
- **reload 不重建配置文件**:运行期文件缺失直接报错保留旧配置,生成仅发生在启用期。
- **平台→游戏为纯文本一行**(`` <sender> content ``):无前缀/颜色定制位,差异化留未来。

## 显式非目标

- **非玩家聊天源不转发**:控制台 `say` 等系统广播不进互通;聊天入口唯一为 `AsyncChatEvent`(玩家源)。
- **不做多版本平台模块**:fabric / velocity 仅为 `settings.gradle.kts` 注释预留,无代码骨架。
- **不自建白名单存储**:白名单 SSOT = MC 原生 `whitelist.json`,网关只是命令派发的结构化封装。
- **无嵌入式协议端**:不做对端内嵌(全量线 embedded 形态的能力),外部对端连入。

## 文档化时明确写"无"的项

帧大小上限配置、库级心跳配置、Jetty / Gson 依赖(JSON = Jackson,WS = Java-WebSocket)、runtime / embedded 配置段、`/kurobridge` 的 reload 以外子命令——以上均不存在于代码中,新增文档勿凭空补写。
