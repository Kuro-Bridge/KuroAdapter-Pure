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
mise exec java@25 -- ./gradlew build          # 门禁：编译 + spotlessCheck + 全部测试
mise exec java@25 -- ./gradlew spotlessApply  # 格式化修复
mise exec java@25 -- ./gradlew clean :paper:shadowJar  # 唯一产物 fat JAR（paper/build/libs/kuroadapter-pure-0.1.0.jar；`build` 可能 up-to-date 跳过重打）
mise exec java@25 -- ./gradlew :core:test     # 仅 :core 测试
```

- 全局 `java` 是 21（不能用），构建统一 `mise exec java@25 -- ...`。
- gradle 输出乱码时：`JAVA_TOOL_OPTIONS="-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"`。
- 只本地提交（master），不建远端、不 push。

## 结构速查

```
:core  com.kurobridge.pure.core     协议层（protocol/）+ 服务端（server/）+ 业务骨架（business/）
:paper com.kurobridge.pure          插件主类 + paper-plugin.yml（适配层，2026-09-18 完成事件/命令/调度接线）
core/src/test/resources/fixtures/   KuroProtocol 金样本 pin（v0.4，16 份）+ PIN.md
```
