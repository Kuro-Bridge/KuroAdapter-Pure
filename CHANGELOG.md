# 变更日志

本文件按版本记录对外可见变更。设计与边界见 [docs/README.md](docs/README.md)，已知边界见 [docs/architecture/limits.md](docs/architecture/limits.md)。

## 1.0.0 — 2026-10-26

首个公开发布：纯 Java Paper 插件，零 Node、零子进程。

### 发布物

- `kuroadapter-pure-1.0.0.jar`：Paper universal 单 JAR，覆盖 1.12.2 ~ 26.3（Java 8 字节码）。

### 能力

- `kurobridge-ws` 0.4.0 服务端：握手、token 鉴权、心跳与空闲检测、频道绑定 fan-out。
- 双向聊天、进服/退服/死亡事件、状态查询、群管理员指令执行。
- 配置 `config.jsonc`（注释 + 行尾逗号）：首启生成带注释模板与 32 位 hex 随机 token；旧 `config.json` 回退读取。
- 金样本一致性门禁：`:core` 测试逐帧对表 KuroProtocol v0.4 的 16 份样本。

### 兼容性

- Paper 1.12.2 ~ 26.3（Java 8 字节码）；1.12.2 与 1.13 ~ 1.16.4 未上机（字节码与 API 层已覆盖）。
- 协议 `kurobridge-ws` 0.4.0（子协议 `kurobridge-ws.v1`）。

### 已知限制

- 仅 Paper 线，无 Fabric / NeoForge / 基岩版适配。
- 协议不内置 TLS，公网部署需隧道或反向代理。
- 完整边界见 [docs/architecture/limits.md](docs/architecture/limits.md)。
