# 决策：fixtures 包内消费机制（线 2 块 C，2026-09-18）

状态：已裁决，随本线实装。本文档先于代码落地（治理原则 3）。

## 0 输入与证据（2026-09-18 勘察）

| 事实 | 证据 |
|---|---|
| PIN.md 登记待办：「按 KuroProtocol ADR-003，改为从已发布的 npm 包消费 fixtures……届时消灭手拷与本表」 | `core/src/test/resources/fixtures/PIN.md:27-28` |
| 守卫三件套：SHA256SUMS 逐文件重算、SUMS 清单集合==目录实际 `*.json` 集合（份数守卫已升级为集合一致性）、`PROTOCOL_VERSION`→`v0.4` 目录推导 | `core/src/test/java/com/kurobridge/pure/core/fixtures/FixtureConformanceTest.java:58-84, 104-111` |
| fixtures 定位：classpath 资源 `/fixtures/v0.4`→落盘 Path→`Files.walk`；目录 `core/src/test/resources/fixtures/v0.4/`（16 JSON+SUMS），git 全跟踪，工作区 LF | 勘察 3 §1.1-1.2、`.gitattributes`（`* text=auto eol=lf`） |
| npm 包 `@kuro-bridge/protocol@0.4.0` 内 `fixtures/v0.4/` 与仓内手拷件**字节级完全一致**（含 SHA256SUMS 本体；SUMS 路径相对 v0.4 目录、无前缀）；包内自洽 `sha256sum -c` 16 条全 OK | 勘察 3 §3（临时目录 `…\Temp\kuro-fixtures-probe-20260918` 保留备查） |
| ADR-003：选 A 随主包分发；`files:["dist","fixtures","scripts/verify-fixtures.mjs"]`+bin；**不新增 exports 子路径**——金样本是物理契约，消费=文件读取+SHA256SUMS 比对，不经模块 import；包内校验=目录↔SUMS 两方，版本目录名由包 version 推导 | `KuroProtocol/docs/DECISIONS.md:189-235` |
| ADR-003 对消费方建议（记录不实施）：依赖 `@^0.4.0`，测试资源改从包内消费+SUMS 校验，淘汰手拷 | `DECISIONS.md:223-230` |
| tarball 可直取：`https://registry.npmjs.org/@kuro-bridge/protocol/-/protocol-0.4.0.tgz`（dist.integrity sha512 已登记在案，临时目录实测本地重算一致） | 勘察 3 §3 |
| Pure 构建现状：Gradle 9.7.0，零 npm/node 任务、零 devEngines；:core 已有 Jackson 2.18.0；JUnit 5 | 勘察 3 §1.4 |
| `ProtocolVersions.PROTOCOL_VERSION="0.4.0"` 为人工同步副本（SSOT 在 KuroProtocol src/meta.ts） | `core/src/main/java/com/kurobridge/pure/core/protocol/ProtocolVersions.java` |

## 1 候选比较

- **候选 a：构建期拉包**——fixtures 移出 git，每次构建（或 check）从 npm tarball 拉取+校验进 test resources。
  优点：零拷贝、单一来源。缺点：测试/构建新增网络硬依赖，离线构建破裂；Gradle 无内置下载任务与 tar 解压（JDK 无 tar），需引 commons-compress 之类新依赖或依赖环境工具链；fixtures 漂移不可在 PR diff 里审阅。否决主因：**离线可构建性**（SMOKE 已登记本机离线是现实约束）与新增依赖触 AGENTS 白名单。
- **候选 b：机械刷新 + 常驻守卫**——fixtures 保持 git 跟踪；提供一次性机械刷新路径从 npm 包重拷；每次测试仍跑全量内容守卫。
  优点：离线可构建；PR diff 可审（协议 bump 逐字节可见）；守卫（单一权威）不变；零新运行依赖。
  缺点：仓内仍存副本（接受：副本即 pin 的物化，守卫使其不可漂移）。
- **候选 c：node 工具链集成（com.github.node-gradle 等）**——为 16 个 JSON 引入 node 插件与 npm 解析依赖，重且越界。否决。

## 2 裁决：候选 b（Gradle 按需机械刷新 + 守卫不变）

- **新增 Gradle 任务 `refreshFixtures`（:core，显式调用，不挂入 build/check 依赖图）**：
  1. 从 `ProtocolVersions.java` 源码正则提取 `PROTOCOL_VERSION`（如 `0.4.0`），机械推导 `v0.4` 目录名与 tarball URL `https://registry.npmjs.org/@kuro-bridge/protocol/-/protocol-<全版本>.tgz`（版本推导断言由此续接：常量改→URL 与目录同步变）。
  2. `java.net.http.HttpClient` 下载至 `build/fixtures-pkg/`（HTTP 404→指路报错：先发 npm 版再 bump 常量）。
  3. 系统 `tar -xzf` 解包（Win10+ 自带 bsdtar，Git Bash/类 Unix 自带；不引解压依赖）。
  4. 用解包件**整体替换** `core/src/test/resources/fixtures/` 下对应 `v<主.次>` 目录；删除其余陈旧 `v*` 目录（机械 bump，不留孤儿目录）。
  5. 打印后续手工步骤提醒（更新 PIN.md 来源登记行）。
- **守卫单一权威不变**：`FixtureConformanceTest` 三件套原样保留且仍是唯一权威——刷新结果由它裁决。刷新 runbook：`gradlew refreshFixtures` 后跑 `gradlew test --tests "*FixtureConformanceTest*"`（写入 PIN.md）。
- **锚链**：`ProtocolVersions` 常量 → tarball URL（npm registry，版本一经发布不可变）→ 解包 → 包内 SHA256SUMS 逐文件校验（测试常驻）→ 语义消费。手工拷贝出局：协议 bump = 改常量 → 跑一个命令 → 门禁裁决。
- **离线**：常规构建/测试零网络（fixtures 跟踪在库）；仅显式 refresh 需网络。
- **验收（本块硬证据）**：对当前 pin 执行 `refreshFixtures` → `git diff` 为空（round-trip 字节级同一，勘察已证包内=仓内一致）；门禁全绿。

## 3 PIN.md 改写要求（块 C）

从"手拷机制+本表"改写为**新机制的事实描述**：来源=npm 包（包名/版本/tag commit `09a1131`/dist.integrity sha512 值照录）；刷新 runbook；bump 流程（KuroProtocol 发版→npm publish→改 `ProtocolVersions`→`refreshFixtures`→全量门禁→本表来源行更新）；守卫三件套说明保留；注明"仓内副本是 pin 的物化，刷新任务+常驻守卫使其可机械复现"。

## 4 边界与红线

- 金样本内容冻结：本块**不得改动任何 `v0.4/*.json` 与 SHA256SUMS 内容**（验收即 round-trip 空 diff）。
- 不动 `FixtureConformanceTest` 守卫逻辑；不动 KuroProtocol（只读）；npm 仅 pack/view/registry 下载（`build.gradle.kts` 内 URL 直取，不用 npm CLI，规避 devEngines 干扰）。
- 门禁全绿后 Conventional 中文提交，master 直提，不 push。
