<div align="center">

<br />

<img src=".github/assets/wordmark.svg?v=2" alt="KuroBridge Pure" width="800">

**Minecraft 服务器与社交平台群服互通 · 纯净线服务端**

<p>
  <img src="https://img.shields.io/badge/角色-WS%20服务端-6366F1?style=flat-square&labelColor=18181B" alt="角色：WS 服务端">&emsp;
  <img src="https://img.shields.io/badge/协议-kurobridge--ws%20v0.4.0-0EA5E9?style=flat-square&labelColor=18181B" alt="协议：kurobridge-ws v0.4.0">&emsp;
  <img src="https://img.shields.io/badge/Java-工具链%2025%20·%20release%2021-10B981?style=flat-square&labelColor=18181B" alt="Java：工具链 25 · release 21">&emsp;
  <img src="https://img.shields.io/badge/许可-MIT-8B5CF6?style=flat-square&labelColor=18181B" alt="许可：MIT">
</p>

<p>
  <a href="#快速开始">快速开始</a> &nbsp;·&nbsp;
  <a href="#双线选型">双线选型</a> &nbsp;·&nbsp;
  <a href="#配置">配置</a> &nbsp;·&nbsp;
  <a href="#文档导航">文档导航</a> &nbsp;·&nbsp;
  <a href="#english">English</a>
</p>

<br />

</div>

---

## 生态定位

**KuroBridge** 是「Minecraft 服务器 ↔ 聊天平台」双向互通产品线，本仓（`KuroAdapter-Pure`）是其**纯净线服务端**实现。全生态拓扑与协议消费关系如下：

<details>
<summary><b>点击展开：KuroBridge 全生态拓扑与协议消费关系图</b></summary>

```mermaid
flowchart TB
    subgraph SSOT ["协议契约 SSOT · KuroProtocol（独立仓）"]
        Proto["<b>@kuro-bridge/protocol ^0.4.0</b><br/><sub>kurobridge-ws v0.4.0 · Zod Schema · 对端规格书 · 16 份金样本</sub>"]
    end

    subgraph MC ["MC 侧 · WS 服务端（被动监听，绝不回连）"]
        direction LR
        Full["<b>KuroAdapter（全量线）</b><br/><sub>跨平台薄壳 + 内嵌 Node 业务核心</sub>"]
        Pure["<b>KuroAdapter-Pure（本仓 · 纯净线）</b><br/><sub>纯 Java Paper 插件 · 零 Node</sub>"]
    end

    subgraph Chat ["聊天侧 · WS 客户端（主动连入，自担重连）"]
        direction LR
        Koishi["<b>Adapter-koishi（官方对端）</b><br/><sub>koishi-plugin-kurobridge (Koishi v4)</sub>"]
        Nap["<b>napukettoqq / 第三方对端</b><br/><sub>原生实现 kurobridge-ws 客户端</sub>"]
    end

    Proto -. "npm 依赖单向消费" .-> Full & Koishi & Nap
    Proto -. "金样本与规格对齐" .-> Pure

    Koishi & Nap == "主动连入 · kurobridge-ws 双向帧通信" ==> Full & Pure

    style Proto fill:#0EA5E9,stroke:#0284C7,color:#ffffff,stroke-width:2px
    style Pure fill:#6366F1,stroke:#4F46E5,color:#ffffff,stroke-width:2px
    style Full fill:#18181B,stroke:#52525B,color:#E4E4E7,stroke-width:1px
    style Koishi fill:#18181B,stroke:#10B981,color:#E4E4E7,stroke-width:1.5px
    style Nap fill:#18181B,stroke:#10B981,color:#E4E4E7,stroke-width:1.5px
```

</details>

* **本仓只做 Java**：协议编解码、WS 服务端与业务骨架全部 Java 手写，构建与运行不依赖 Node。
* **与全量线行为等价**：契约 SSOT 同为 [KuroProtocol](https://github.com/Kuro-Bridge/KuroProtocol)（`kurobridge-ws` v0.4.0），以金样本一致性门禁逐帧对齐。

### 边界与职责（身份卡）

**本仓负责**

* **`kurobridge-ws` 服务端**：子协议协商、握手、鉴权、心跳与帧收发，全部 Java 手写

* **事件桥接**：chat / join / quit / death / status 按绑定频道 fan-out；平台消息回投为游戏内全服广播

* **配置与管理**：首启自动生成配置、`/kurobridge reload` 热重载、管理员命令判定与 MC 白名单网关

* **金样本一致性门禁**：`:core` 测试 pin KuroProtocol v0.4 的 16 份金样本逐帧对表

**本仓不负责**

* **协议契约定义**：SSOT 位于 [KuroProtocol](https://github.com/Kuro-Bridge/KuroProtocol)，本仓仅以金样本对表

* **聊天平台适配**：QQ / Telegram / Discord 等协议对接由对端侧框架承担

* **内嵌协议端**：不内置 napuketto——外部对端（napukettoqq / Adapter-koishi 等）主动连入

* **宿主运行环境**：Paper 服务端本体与 JDK 工具链由用户自备

---

## 双线选型

**全量线 [KuroAdapter](https://github.com/Kuro-Bridge/KuroAdapter)**

* Paper JAR：Java 薄壳看护内嵌 Node 子进程，业务核心经 IPC 帧通信

* TS 直接消费 zod schema（npm 包）保证协议一致性

* embedded 形态可内置 napuketto（开箱扫码）

* 平台面：Paper / Fabric / LSE 已实现，Endstone 已构建

* 适合开箱扫码、多平台覆盖

**纯净线 KuroAdapter-Pure（本仓）**

* Paper 插件：单 JVM 进程——零 Node、零子进程、零 IPC

* 金样本门禁：v0.4 的 16 份样本逐帧对表保证协议一致性

* 无内嵌协议端：外部对端（napukettoqq / Adapter-koishi 等）连入

* 平台面：Paper（1.21.4 真机验证）

* 适合部署极简、无 Node 依赖的服务器

---

## 快速开始

```bash
mise run build    # 门禁：编译 + spotlessCheck + 全部测试（= CI ./gradlew build）
mise run jar      # 确定性重打 fat JAR：clean + :paper:shadowJar
```

* 构建入口 = mise tasks（定义于 `mise.toml [tasks]`，经 `mise exec java@25` 保证 JDK 25）；偶用 gradle 原生命令：`mise exec java@25 -- ./gradlew <task>`。

* 唯一可装产物：`paper/build/libs/kuroadapter-pure-0.1.0.jar`（shadowJar，内含 `:core`；薄壳与根空壳 jar 已在构建脚本中禁用），丢进 Paper 服务器 `plugins/` 即可。

* 坑：`build` 目标下的 shadowJar 可能因 up-to-date 被跳过（jar mtime 不刷新）——需要确定性重打时用 `mise run jar`（先 clean）。

---

## 当前能力

已接线（回环单测 + 编译门禁 + Paper 1.21.4 真机冒烟验证）：

* 配置读取与**首启自动生成**（`plugins/KuroBridgePure/config.json`，生成件即文档）；

* WS 服务端：子协议协商 / 握手 / 鉴权 / 心跳；端口绑定失败 SEVERE 指路后中止启用；

* chat / join / quit / death / status 事件按绑定频道 fan-out；平台消息回投为游戏内全服广播（`<sender> content` 纯文本一行）；

* 业务动作经 BukkitScheduler 主线程投递；

* 管理员命令判定（先于执行）+ MC 白名单网关（`dispatchCommand` CONSOLE 语义）；

* `/kurobridge reload` 配置热重载（语义见下）。

<details>
<summary><b>已知边界</b>（最新口径见 <a href="docs/architecture/limits.md">docs/architecture/limits.md</a>）</summary>

* `query status` 返回最近一帧缓存，事件稀疏场景可滞后（与主仓一致的接受语义）；

* `version` 命令仅捕获同步首行输出，异步余量直落控制台（收集窗口设计边界，裁决不修）；

* 控制台 `say` 等非玩家聊天源不转发——**显式非目标**；

* fabric / velocity 多版本平台模块为预留位。

</details>

---

## 配置

**首启自动生成**：文件缺失时插件生成安全默认配置并落盘（生成件即文档），随后按正常解析路径加载启用。生成件形状：

```json
{
  "channels": [],
  "token": "<32 位随机 hex>",
  "admins": [],
  "server": { "id": "kurobridge-pure" },
  "ws": { "host": "127.0.0.1", "port": 25580 }
}
```

`token` 由 SecureRandom 生成，日志只指路文件路径不回显值——**对端须从生成文件抄取 token 并配置同一令牌**。

逐字段语义：

* **`channels`**（必填，可空数组）：绑定频道名列表，去重保序；`reload` **热更**（变化自动 `bindings_updated` 广播）。

* **`token`**（缺省 `""`）：WS 鉴权令牌；**空 token（缺失或 `""`）拒绝启动 WS 监听**并指路；进程固定。

* **`admins`**（缺省 `[]`）：`{channel, users}` 管理员映射，命中才放行 command 帧；channel / users 各自去重保序；`reload` **热更**。

* **`server.id`**（缺省 `"kurobridge-pure"`）：`hello_ack` 上报的服务器标识（非空字符串）；进程固定。

* **`ws.host`**（缺省全部接口）：监听地址；进程固定。

* **`ws.port`**（缺省动态端口）：1-65535；对端用静态 url 互联须固定；进程固定。

* `ws` 整段不写 = 全部接口 + 动态端口；生成件显式给 `127.0.0.1:25580`（环回 + 固定端口是安全默认，也是对端静态 url 的开箱即用值）；只配 `host` 不配 `port` 合法。

* 配置形状非法 / `server.id` 空串或非字符串：SEVERE 后中止启动（`ConfigException`，不静默吞）。

* 端口被占：SEVERE 指路修改 `ws.port` 后重启——不回退动态端口（回退会静默破坏对端静态 url）。

* 迁移（存量配置）：原先无 token 或为空串的，**设任意非空 token 即可**（对端须同配；仅本机调试可用任意非空串如 `dev`）。

### /kurobridge reload

* **热更**：`channels`（集合变化自动触发 `bindings_updated` 广播）、`admins`（管理员表重建原子换入，下一次 command 判定即用新表）。

* **待重启**：`token` / `ws.host` / `ws.port` / `server.id`（进程固定，回执明示）。

* 权限：`kurobridge.reload`（paper-plugin.yml 声明 default op；控制台恒可）。

* 注册方式：Paper 1.21.4 的 paper-plugin.yml **不支持 `commands` 块**，命令经 Lifecycle / Brigadier API（`LifecycleEvents.COMMANDS` + `BasicCommand`）注册。

* 行为：读盘重解析 → 解析失败则**保留旧配置**并回显原因（文件缺失不重新生成）→ 成功则按上述语义热更并回执【已热更】/【待重启】两组。

手写配置最小示例（编辑生成件或自建）：

```json
{
  "channels": ["minecraft"],
  "token": "s3cret",
  "admins": [{ "channel": "minecraft", "users": ["10001"] }],
  "ws": { "port": 8081 }
}
```

---

## 模块与一致性

<details>
<summary><b>点击展开：单 JVM 插件内部架构图</b></summary>

```mermaid
flowchart TB
    subgraph Plugin ["Paper 插件 · 单 JVM 进程（kuroadapter-pure-0.1.0.jar）"]
        direction TB
        Paper["<b>:paper · Paper 适配层</b><br/><sub>插件生命周期 · Bukkit 事件 / 命令 / 调度 / 白名单桥接</sub>"]
        Core["<b>:core · 协议层 + 业务骨架（零 Bukkit API）</b><br/><sub>帧编解码 · 握手 / 版本协商 / 鉴权 / 心跳 · 未知帧容忍</sub>"]
        WS["<b>kurobridge-ws 服务端</b><br/><sub>监听 ws.host:ws.port · 子协议 kurobridge-ws.v1</sub>"]
        Core <--> WS
        Paper <-->|"Bukkit 事件入 · 游戏动作主线程投递"| Core
    end

    Peer["聊天对端进程<br/><sub>koishi-plugin-kurobridge / napukettoqq 等</sub>"]
    Peer == "WS 连入 · 双向帧通信" ==> WS

    style Core fill:#6366F1,stroke:#4F46E5,color:#ffffff,stroke-width:2px
    style WS fill:#0EA5E9,stroke:#0284C7,color:#ffffff,stroke-width:2px
    style Paper fill:#18181B,stroke:#52525B,color:#E4E4E7,stroke-width:1px
    style Peer fill:#18181B,stroke:#8B5CF6,color:#E4E4E7,stroke-width:1.5px,stroke-dasharray: 4 4
```

</details>

* **`:core`**：协议层——帧编解码 / 握手 / 版本协商 / 鉴权 / 心跳 / 未知帧容忍 + WS 服务端 + 业务骨架；**零 Bukkit API**。

* **`:paper`**：Paper 适配层——插件生命周期 + Bukkit 事件 / 命令 / 调度 / 白名单桥接。

* **金样本一致性门禁**：`:core` 测试 pin KuroProtocol v0.4 金样本 16 份（`core/src/test/resources/fixtures/`），`FixtureConformanceTest` 逐帧对表；`mise run fixtures` 从 npm 包机械刷新。测试基线：`:core` 10 个测试类 / 103 用例。

* **工具链**：Java 25 / `release 21` · Gradle 9.7 · Spotless（palantir）· JUnit 5 · `-Xlint:all -Werror`。

* 文档体系：[docs/README.md](docs/README.md)（架构 / 协议规格 / 模块机制 / 开发维护）；工程规范：[AGENTS.md](AGENTS.md)。

* 对端接入实现依据：[peer-guide](https://github.com/Kuro-Bridge/KuroProtocol/blob/main/docs/peer-guide.md)。

---

## 文档导航

| 核心主题 | 推荐阅读入口 |
| :--- | :--- |
| **定位、模块边界与架构红线** | [架构总览](docs/architecture/overview.md) |
| **线程模型与主线程投递** | [线程模型](docs/architecture/threading.md) |
| **线上格式 / 会话握手 / 帧目录** | [线上格式与版本兼容](docs/protocol/wire-format.md) · [会话与握手](docs/protocol/session.md) · [帧目录](docs/protocol/messages.md) |
| **`:core` 服务端与配置机制** | [PureWsServer：WS 服务端机制](docs/modules/core-server.md) · [配置体系](docs/modules/core-config.md) |
| **`:paper` 生命周期与命令链路** | [Paper 插件：生命周期与事件接线](docs/modules/paper-plugin.md) · [命令链路](docs/modules/paper-commands.md) |
| **构建、测试与已知边界** | [构建与依赖](docs/development/build.md) · [测试与金样本门禁](docs/development/testing.md) · [已知边界与显式非目标](docs/architecture/limits.md) |
| **全仓文档总索引** | [文档中心](docs/README.md) |

---

## 状态

![Alt](https://repobeats.axiom.co/api/embed/4931b3cd416694c2bcfc9ad6868b9ca4dd561198.svg "Repobeats analytics image")

---

## English

KuroAdapter-Pure is the **pure-Java** implementation of the `kurobridge-ws` WebSocket server as a Paper plugin — no Node.js, no subprocess, no IPC. It shares the protocol contract with the full-line KuroAdapter through the golden fixtures published in `@kuro-bridge/protocol` (v0.4, 16 samples), enforced by conformance tests. Drop `kuroadapter-pure-0.1.0.jar` into `plugins/`; the plugin generates a safe config on first start (random 32-hex token) and refuses to open the listener while the token is empty.

```bash
mise run build && mise run jar
```

*Released under the [MIT License](./LICENSE).*
