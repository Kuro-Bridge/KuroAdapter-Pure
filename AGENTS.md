# KuroAdapter-Pure 工程指南（AGENTS）

> 开始任何工作前，先读本文件与 `docs/STATUS.md`（现状）→ `docs/design.md`（设计书）。

## 项目是什么

KuroAdapter-Pure：KuroBridge 纯净线的**纯 Java Paper 插件**——kurobridge-ws v0.4.0 协议
的 WS 服务端。与主仓（KuroAdapter：Paper JAR + Node 子进程）协议行为一致，但零 Node、
零子进程、零 IPC、零嵌入式胶水。

## 红线（违反 = 错误）

1. **零胶水**：禁止引入 Node/子进程/IPC/嵌入式相关的一切（代码、依赖、配置段）。
2. **运行时依赖白名单**：Paper API（compileOnly）、Jackson（jackson-databind）、
   Java-WebSocket（MIT）、log4j-core（compileOnly——vanilla 命令输出捕获，运行期由 Paper
   服务端自带，依据 `docs/history/PAPER-WIRING-2026-09-18.md` §4）。测试 JUnit 5、
   格式化 Spotless。白名单之外零引入。
3. **主仓与姊妹仓只读**：工作区内与本仓并列的 `../KuroAdapter`（行为基准
   `bridge/core/src/server.ts`）与 `../KuroProtocol`（协议 SSOT + fixtures）只读；
   fixtures 拷贝进本仓测试资源消费并保持 PIN.md 记录。
4. **协议契约 = KuroProtocol 仓** `docs/peer-guide.md` + `src/frame.ts` /
   `src/messages/ws.ts` / `src/meta.ts`；一致性门禁 = `fixtures/v0.4/` 金样本
   （`core/src/test/resources/fixtures/`，PIN.md 记录来源与日期）。协议 bump 时：
   更新 `ProtocolVersions` 副本 → 重新拷贝 fixtures → 更新 PIN.md，三件事同批落地。
5. **`:core` 零 Bukkit API**：协议层与业务骨架可独立 JUnit 测试；平台适配只出现在 `:paper`。
6. **`:paper` 不做协议**：只做 Bukkit 桥接（事件/命令/调度/生命周期）。

## 风格

- Java 工具链 25、字节码 **release 21**（对齐主仓 ADR-015）、`-Xlint:all -Werror`、UTF-8。
- Spotless + palantirJavaFormat 2.71.0（>=2.71.0 才兼容 JDK 25）；提交前 `spotlessApply`。
- 类型化错误（`ProtocolException` / `ConfigException`），不静默吞；日志走注入的 `KbLogger`。
- 提交信息中文，`docs:` / `chore:` / `feat:` / `test:` / `fix:` 前缀。
- 本仓 git：`commit.gpgsign=false`、`core.autocrlf=false` 已配置，勿改回。

## 常用命令

```bash
mise run build      # 门禁：编译 + spotlessCheck + 全部测试（= CI ./gradlew build）
mise run format     # 格式化修复（spotlessApply；提交前跑）
mise run jar        # 唯一产物 fat JAR（clean :paper:shadowJar → paper/build/libs/kuroadapter-pure-0.1.0.jar）
mise run fixtures   # 金样本机械刷新（需网络；显式任务不挂 build 图，后续手工步骤见任务输出）
```

- 构建入口 = mise tasks（定义于 `mise.toml [tasks]`，跨平台经 bash 执行 gradlew，mise exec
  保证 JDK 25——全局 `java` 是 21 不能用）。偶用 gradle 原生命令仍可
  `mise exec java@25 -- ./gradlew <task>`（如 `:core:test` 仅跑 :core 测试）；CI 走原生
  `./gradlew build`，两边同一条任务图。
- gradle 输出乱码时：`JAVA_TOOL_OPTIONS="-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"`。
- 远端在册（origin=Kuro-Bridge/KuroAdapter-Pure，已切 canonical）；提交默认本地落 master，推送由用户决策；CI（`.github/workflows/ci.yml`）于 push/PR 触发 build 门禁。

## 结构速查

```
:core  com.kurobridge.pure.core     协议层（protocol/）+ 服务端（server/）+ 业务骨架（business/）
:paper com.kurobridge.pure          插件主类 + paper-plugin.yml（适配层，2026-09-18 完成事件/命令/调度接线）
core/src/test/resources/fixtures/   KuroProtocol 金样本 pin（v0.4，16 份）+ PIN.md
```
