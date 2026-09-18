# KuroAdapter-Pure

KuroBridge 纯净线第一站：**纯 Java Paper 插件**，实现 `kurobridge-ws` v0.4.0 协议的
WS 服务端——外部协议端（napukettoqq / koishi-plugin-kurobridge / 其它实现）以 WS 客户端
身份主动连入，与 MC 服务器互通报文。与主仓 KuroAdapter（Paper JAR + Node 子进程）协议
行为一致，但零 Node、零子进程、零 IPC、零嵌入式胶水；协议契约 SSOT 在姊妹仓
[KuroProtocol]（`C:\Dev\MC-Ecosystem\KuroProtocol`），金样本夹具做两仓一致性门禁。

## 快速开始

```bash
mise exec java@25 -- ./gradlew build                  # 门禁（编译 + spotlessCheck + 测试）
mise exec java@25 -- ./gradlew clean :paper:shadowJar # 可靠重打 fat JAR
```

- 唯一可装产物：`paper/build/libs/kuroadapter-pure-0.1.0.jar`（shadowJar fat JAR，内含
  :core；薄壳与根空壳 jar 已在构建脚本中禁用），丢进 Paper 服务器 `plugins/` 即可。
- 坑：`build` 目标下的 shadowJar 可能因 up-to-date 被跳过（jar mtime 不刷新）——需要
  确定性重打时用上面的 `clean :paper:shadowJar`。

## 当前能力（分阶段现状，详见 docs/STATUS.md）

已接线（回环单测 + 编译门禁验证，未上真机）：

- 配置读取（`plugins/KuroBridgePure/config.json`）+ WS 服务端启动（子协议协商/握手/鉴权/心跳）；
- chat / join / quit / death / status 事件按绑定频道 fan-out；平台侧消息回投为游戏内
  全服广播（`<sender> content` 纯文本一行）；
- 业务动作经 BukkitScheduler 主线程投递；
- 管理员命令判定（先于执行）+ MC 白名单网关（`dispatchCommand` CONSOLE 语义）。

未做（`docs/STATUS.md` 下一阶段清单）：

- 配置落盘（首启**不**生成 config.json）与 `/kurobridge reload` 热重载；
- 真机联调（Paper 服务器 + 真实对端全链路验证）。

**首启须手写 `plugins/KuroBridgePure/config.json`**，最小示例：

```json
{
  "channels": ["minecraft"],
  "token": "s3cret",
  "admins": [{ "channel": "minecraft", "users": ["10001"] }],
  "ws": { "port": 8081 }
}
```

- `channels` 必填（可空数组，元素为绑定的频道名）；`admins` 缺省 = 无人可经 command 帧
  执行命令；`token` 缺省或空串 = 不鉴权；
- `ws` 段缺省 = **动态端口 + 全部接口**（每次启动端口都可能变）——对端互联须固定
  `ws.port`（1-65535；`host` 可单独省略 = 全部接口）；
- 配置形状非法时插件 SEVERE 后中止启动（`ConfigException`，不静默吞）。

- 设计书：`docs/design.md`；现状与路线图：`docs/STATUS.md`；工程规范：`docs/AGENTS.md`。
- 对端接入实现依据：KuroProtocol 仓 `docs/peer-guide.md`。

## 模块

| 模块 | 职责 |
|---|---|
| `:core` | 协议层（帧编解码/握手/版本协商/鉴权/心跳/未知帧容忍）+ WS 服务端 + 业务骨架，零 Bukkit API |
| `:paper` | Paper 适配层（插件生命周期 + 事件/命令/调度/白名单桥接，2026-09-18 完成接线） |

[KuroProtocol]: https://github.com/Oppenheymu/KuroProtocol
