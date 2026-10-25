# 架构总览

KuroBridge Pure(KuroBridge 纯净线,仓库名 KuroAdapter-Pure)是**纯 Java 实现的 `kurobridge-ws` WS 服务端**,打包为 Paper 插件——零 Node、零子进程、零 IPC、零嵌入式胶水。生态位置与双线选型见根 [README](../../README.md);工程红线(零胶水、依赖白名单、主仓/姊妹仓只读、`:core` 零 Bukkit API、`:paper` 不做协议)见 [AGENTS.md](../../AGENTS.md),本文不重复。

## 模块划分

```text
:core   com.kurobridge.pure.core      协议层 + 业务骨架 + WS 服务端;零 Bukkit API,可独立 JUnit 测试
:paper  com.kurobridge.pure(.paper)   Paper 适配薄壳:插件生命周期 + Bukkit 事件/命令/调度接线
```

- 依赖方向:`:paper` → `:core`(implementation);`:core` 不依赖任何平台 API。
- `:core` 内部分三包:
  - `protocol` — 帧编解码、消息体、收发校验、版本兼容([wire-format.md](../protocol/wire-format.md) / [messages.md](../protocol/messages.md));
  - `server` — WS 服务端、每连接状态机、调度与超时抽象([core-server.md](../modules/core-server.md) / [session.md](../protocol/session.md) / [threading.md](threading.md));
  - `business` — 配置模型与解析、绑定存储、扩展点接口([core-config.md](../modules/core-config.md))。
- 多平台预留:`settings.gradle.kts` 注释声明未来可扩展 fabric / velocity 模块(复用 `:core`,只换适配层)——**仅注释,无代码骨架**(见 [limits.md](limits.md))。

## 与全量线(KuroAdapter)的差异

| 维度 | 全量线 | 本仓(纯净线) |
| :--- | :--- | :--- |
| 架构 | Paper JAR 看护内嵌 Node 子进程,stdin/stdout JSON-lines IPC | 纯 Java 单体,业务与协议同 JVM |
| WS 服务端 | Node `ws` 库(引导层) | Java-WebSocket(`:core` 内 `PureWsServer`) |
| 协议一致性 | TS 直接消费 zod schema(npm 包) | 金样本门禁:16 份样本逐帧对表([testing.md](../development/testing.md)) |
| 配置 | 含 runtime / embedded 段 | 去掉两段,其余形状对齐([core-config.md](../modules/core-config.md)) |
| 嵌入式 | 可内置协议端 | 无——外部对端连入 |

保留不变:Gradle 多模块形态、工具链(Java 25)、Spotless(palantir)、shadow fat JAR 产物。universal 基线(对齐主仓 ADR-038):`:core`/`:paper` main 源集 `--release 8` + 经典 plugin.yml,单 jar 兼容 Paper 1.12.2 ~ 26.3。

## 业务扩展点

`:core` 的 `business` 包定义三个注入口,当前均处于"接口就位、最小实现"状态:

| 扩展点 | 接口 | 当前实现 | 现状说明 |
| :--- | :--- | :--- | :--- |
| 频道绑定表 | `BindingStore` | `ConfigBindingStore`(SSOT = 配置 `channels`) | 已接线:`replace` 变化即触发 `bindings_updated` 广播([core-config.md](../modules/core-config.md)) |
| 转发规则 | `ForwardRules` | `ForwardRules.EMPTY` 恒放行 | 绑定表过滤之外的**二次策略位**(双向各一个判定);规则配置化留作后续增强 |
| 白名单网关 | `WhitelistGateway` | `PaperWhitelistGateway`(list/add/remove future 化,经命令派发) | **结构化 API 当前无调用方**——command 帧直传 `whitelist ...` 字符串已达同效;语义走 MC 原生 whitelist 命令,SSOT = `whitelist.json`,本仓不自建存储 |

## 代码导航速查

```text
core/src/main/java/com/kurobridge/pure/core/
├── protocol/            帧模型与编解码(Frame/FrameCodec/Frames)、收发校验(In/OutboundFrames)、
│   │                    版本(ProtocolVersions/VersionCompat)、消息体(message/ 13 型)
│   └── server/          PureWsServer、PeerSession、SessionContext、调度/超时/日志抽象、IdleTracker
└── business/            PureConfig/ConfigLoader/ConfigBootstrap/ConfigReloadReport、
                         BindingStore/ForwardRules/WhitelistGateway 接口
paper/src/main/java/com/kurobridge/pure/paper/
                         插件主类、PaperRelay、聊天双路径监听 + 进退服/死亡监听、命令与执行管线、
                         Compat 探测、PlainText 兼容链、调度/日志适配
paper/src/main/resources/plugin.yml
```
