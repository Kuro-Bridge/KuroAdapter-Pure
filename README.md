# KuroAdapter-Pure

KuroBridge 纯净线第一站：**纯 Java Paper 插件**，实现 `kurobridge-ws` v0.4.0 协议的
WS 服务端——外部协议端（napukettoqq / koishi-plugin-kurobridge / 其它实现）以 WS 客户端
身份主动连入，与 MC 服务器互通报文。与主仓 KuroAdapter（Paper JAR + Node 子进程）协议
行为一致，但零 Node、零子进程、零 IPC、零嵌入式胶水；协议契约 SSOT 在姊妹仓 KuroProtocol
（`C:\Dev\MC-Ecosystem\KuroProtocol`），金样本夹具做两仓一致性门禁。

## 快速开始

```bash
mise exec java@25 -- ./gradlew build        # 门禁（编译 + spotlessCheck + 测试）
mise exec java@25 -- ./gradlew :paper:shadowJar
# 产物：paper/build/libs/kuroadapter-pure-0.1.0.jar —— 丢进 Paper 服务器 plugins/ 即可
# 插件监听 WS（默认动态端口；配置 plugins/KuroBridgePure/config.json 的 ws 段可固定 host/port）
```

- 设计书：`docs/design.md`；现状与路线图：`docs/STATUS.md`；工程规范：`docs/AGENTS.md`。
- 对端接入实现依据：KuroProtocol 仓 `docs/peer-guide.md`。

## 模块

| 模块 | 职责 |
|---|---|
| `:core` | 协议层（帧编解码/握手/版本协商/鉴权/心跳/未知帧容忍）+ WS 服务端 + 业务骨架，零 Bukkit API |
| `:paper` | Paper 适配薄壳（本阶段插件空壳，事件接线下一阶段） |

[KuroProtocol]: https://github.com/kurobridge/KuroProtocol
