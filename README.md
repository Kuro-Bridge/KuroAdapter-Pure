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

已接线（回环单测 + 编译门禁验证；真机覆盖：2026-09-18 冒烟 `docs/history/SMOKE-2026-09-18.md`
+ 同日矩阵补格 `docs/history/SMOKE-2026-09-18-2.md`——command/query 往返、death、negate
静音、vanilla 回退、reload、首启自生成、koishi 端到端均已真机实锤）：

- 配置读取与首启自动生成（`plugins/KuroBridgePure/config.json`，见下方配置段）；
- WS 服务端启动（子协议协商/握手/鉴权/心跳）+ 端口绑定失败 SEVERE 指路后中止启用；
- chat / join / quit / death / status 事件按绑定频道 fan-out；平台侧消息回投为游戏内
  全服广播（`<sender> content` 纯文本一行）；
- 业务动作经 BukkitScheduler 主线程投递；
- 管理员命令判定（先于执行）+ MC 白名单网关（`dispatchCommand` CONSOLE 语义）；
- `/kurobridge reload` 配置重载（channels/admins 热更，token/ws/server.id 待重启，见下）。

未做（`docs/STATUS.md` 已知边界与残留）：

- status 快照语义边界：leave 时推送的快照仍计入离开玩家（计数偏大），`query status`
  返回该缓存、事件稀疏场景可滞后；
- `version` 命令仅捕获同步首行输出，异步余量直落控制台；控制台 `say` 等非玩家聊天源
  不转发（协议未承诺）；
- 多版本平台模块（fabric/velocity 预留位）；koishi 对端仍在 0.1.0 线（对端升级属对端线）。

## 配置（plugins/KuroBridgePure/config.json）

**首启自动生成**：文件缺失时插件自动生成安全默认配置并落盘（生成件即文档），随后按正常
解析路径加载继续启用。生成件形状：`channels: []`、`token: <32 位随机 hex>`（SecureRandom，
日志只指路文件路径不回显值）、`admins: []`、`server.id: "kurobridge-pure"`、
`ws: { host: "127.0.0.1", port: 25580 }`。对端须**从生成文件抄取 token** 并配置同一令牌。

逐字段语义：

| 字段 | 缺省（不写时） | 说明 |
|---|---|---|
| `channels` | 无（必填，可空数组） | 绑定频道名列表，去重保序；`reload` **热更**（变化自动 `bindings_updated` 广播） |
| `token` | `""`（空串） | WS 鉴权令牌。**安全默认：空 token（缺失或 `""`）拒绝启动 WS 监听**并指路；进程固定 |
| `admins` | `[]` | 管理员映射 `{channel, users}`（命中才放行 command 帧）；channel/users 各自去重保序；`reload` **热更** |
| `server.id` | `"kurobridge-pure"` | hello_ack 上报的服务器标识（非空字符串）；进程固定 |
| `ws.host` | 全部接口 | 监听地址；进程固定 |
| `ws.port` | 动态端口 | 1-65535；对端用静态 url 互联须固定；进程固定 |

- `ws` 整段不写 = 全部接口 + 动态端口（生成件显式给 `127.0.0.1:25580`——环回 + 固定端口
  是安全默认，也是对端静态 url 的开箱即用值）；只配 `host` 不配 `port` 合法。
- 配置形状非法 / `server.id` 空串或非字符串：SEVERE 后中止启动（`ConfigException`，不静默吞）。
- 端口被占：SEVERE 指路修改 `ws.port` 后重启（不回退动态端口——回退会静默破坏对端静态 url）。
- 迁移（存量配置）：若原先无 token 或为空串，**设任意非空 token 即可**（对端须配置同一令牌；
  仅本机调试可用任意非空串如 `dev`）。

### /kurobridge reload

- 权限：`kurobridge.reload`（paper-plugin.yml 声明 default op；控制台恒可）。
- **注册方式结论**：Paper 1.21.4 的 paper-plugin.yml **不支持 `commands` 块**（插件元数据
  `PluginMeta` 只有 `permissions` 字段），故命令经 Paper Lifecycle/Brigadier API
  （`LifecycleEvents.COMMANDS` + `BasicCommand`）注册。
- 行为：读盘重解析 → 解析失败则**保留旧配置**并回显原因（文件缺失不重新生成）→ 成功则
  按下表热更并回执【已热更】/【待重启】两组（仅列出有变化的字段）。

| 字段 | reload 后 |
|---|---|
| `channels` | 热更（集合变化自动触发 `bindings_updated` 广播） |
| `admins` | 热更（管理员表重建原子换入，下一次 command 判定即用新表） |
| `token` / `ws.host` / `ws.port` / `server.id` | 待重启（进程固定，回执明示） |

手写配置最小示例（编辑生成件或自建）：

```json
{
  "channels": ["minecraft"],
  "token": "s3cret",
  "admins": [{ "channel": "minecraft", "users": ["10001"] }],
  "ws": { "port": 8081 }
}
```

- 设计书：`docs/design.md`；现状与路线图：`docs/STATUS.md`；工程规范：`docs/AGENTS.md`。
- 对端接入实现依据：KuroProtocol 仓 `docs/peer-guide.md`。

## 模块

| 模块 | 职责 |
|---|---|
| `:core` | 协议层（帧编解码/握手/版本协商/鉴权/心跳/未知帧容忍）+ WS 服务端 + 业务骨架，零 Bukkit API |
| `:paper` | Paper 适配层（插件生命周期 + 事件/命令/调度/白名单桥接，2026-09-18 完成接线） |

[KuroProtocol]: https://github.com/Oppenheymu/KuroProtocol
