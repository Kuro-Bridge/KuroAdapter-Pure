# KuroBridge Pure 文档索引

KuroBridge Pure(KuroBridge 纯净线,仓库名 KuroAdapter-Pure):纯 Java 实现的 `kurobridge-ws` v0.4.0 WS 服务端,Paper 插件形态。产品定位、快速开始与配置的用户面说明见根 [README](../README.md);工程红线与常用命令见 [AGENTS.md](../AGENTS.md)。本目录是开发者文档,按四个维度组织,`docs/` 根下仅此一份索引。

## 文档地图

| 文档 | 内容 |
| :--- | :--- |
| **architecture/** —— 系统层 | |
| [overview.md](architecture/overview.md) | 定位、模块划分与依赖方向、与全量线差异、业务扩展点、代码导航 |
| [threading.md](architecture/threading.md) | 线程模型:WS 线程 / 定时器线程 / 业务主线程的归属与跨线程触达 |
| [limits.md](architecture/limits.md) | 已知边界、有意取舍、显式非目标(唯一清单) |
| **protocol/** —— 协议规格 | |
| [wire-format.md](protocol/wire-format.md) | 线上格式、两段式解析、出帧编码、协议版本与兼容策略、SSOT 同步义务 |
| [messages.md](protocol/messages.md) | 13 种帧的完整目录(方向、形态、字段与约束)与易错点 |
| [session.md](protocol/session.md) | 握手状态机、鉴权、超时、关闭码、未知帧容忍 |
| **modules/** —— 模块机制 | |
| [core-server.md](modules/core-server.md) | PureWsServer:子协议门禁、绑定等待、连接回调、广播契约、会话装配 |
| [core-config.md](modules/core-config.md) | 配置体系:config.jsonc 规范(JSONC)、首启注释模板与 token 门禁、旧文件回退、绑定存储、热重载矩阵 |
| [paper-plugin.md](modules/paper-plugin.md) | 插件生命周期、事件接线(含权限静音位)、Relay 出帧与入站 |
| [paper-commands.md](modules/paper-commands.md) | `/kurobridge reload`、command 帧执行管线(双路径输出收集)、白名单网关 |
| **development/** —— 开发维护 | |
| [build.md](development/build.md) | 构建、工具链、产物、依赖白名单、CI、已知构建坑 |
| [testing.md](development/testing.md) | 测试体系、金样本一致性门禁(守卫三件套)、refreshFixtures 与协议 bump 流程 |

## 按角色阅读

- **对端实现者**(开发平台侧 WS 客户端):protocol 三篇 → `limits.md`;协议契约原文(对端视角)另见 KuroProtocol 仓 `docs/peer-guide.md`。
- **部署与运维**:根 README(安装/配置/reload)→ `core-config.md`(字段语义与热冷矩阵细节)→ `limits.md`(行为边界)。
- **本仓维护者**:`overview.md` 起步 → 所改模块的 modules/ 篇 + `threading.md` → 提交前 `build.md` 与 AGENTS.md 规范;协议 bump 走 `testing.md` 的 runbook。

## 约定

- 代码即事实:全部文档以当前源码为准,机制描述均对应到类/方法;与代码冲突时以代码为准并回改文档。
- 对外产品叙事用 **KuroBridge Pure**;技术标识(`kuroadapter-pure`、`plugins/KuroBridgePure/config.jsonc`、`kurobridge-ws` 等)保留原样。
- 全文不使用 Emoji。
