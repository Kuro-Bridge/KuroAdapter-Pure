# 测试与金样本门禁

测试全部在 `:core`(`:paper` 靠 `:core` 覆盖 + 真机验证),JUnit 5,`mise run build` 一并执行。测试体系两层:**自有单测**(逐类行为)+ **金样本一致性门禁**(与协议 SSOT 对表)。

## 自有单测(`core/src/test/java/com/kurobridge/pure/core/`)

| 测试类 | 覆盖面 |
| :--- | :--- |
| `protocol.FrameCodecTest` | 帧往返还原、wire 骨架各拒绝分支(非法 JSON / 根非对象 / 缺 header / type 非 snake_case / id 非 UUID)、请求帧缺 id 与事件帧带 id 的第二段拒绝、未知字段剥离、出帧单行 JSON |
| `protocol.VersionCompatTest` | 主版本相同兼容、不同不兼容、格式非法不兼容 |
| `business.ConfigLoaderTest` | 合法解析与去重保序、缺省补齐、channels/token/admins/ws/serverId 各非法分支、多余字段剥离、ws 四形态、文件加载失败 |
| `business.ConfigBindingStoreTest` | 初始表、replace 变化通知、相同集合 no-op、空集清空、不可变视图 |
| `business.ConfigBootstrapTest` | 生成形状 round-trip、token 32 位 hex 且两次不同、既有文件字节不动、父目录创建、目录创建失败指路、token 门禁矩阵 |
| `business.ConfigReloadReportTest` | 无变化、channels/admins 入热组、token/serverId 入冷组、ws 缺省与显式对比 |
| `server.PeerSessionTest` | 状态机全矩阵(握手成功/旧版本兼容/版本拒绝 1002/鉴权拒绝 1008/token 空、非法 hello 不回执、hello 超时、重复 hello、握手前帧丢弃、ping-pong 回显、空闲超时与入帧重置、未知帧三分支、骨架失败不断连、chat/command/query 各路径、shutdown 1001) |
| `server.PureWsServerTest` | 真实回环(真实端口):端口被占即时抛、子协议正确握手 + 动态端口、子协议缺失 HTTP 400、关服 close 1001、broadcast 送达数、0 送达 |
| `server.StatusCountsTest` | quit 窗口剔除计数的四分支 |
| `fixtures.FixtureConformanceTest` | 金样本门禁(下节) |

测试工具在 `server.Fakes`:`ManualScheduler`(手动推进定时器,确定性断言超时)、`FakeConnection`、`CollectingLogger`、`RecordingHooks`。

具体用例数不落本文档(会腐化),以 `mise run build` 实跑输出为准。

## 金样本一致性门禁

协议契约的 SSOT 在 KuroProtocol 仓;两仓一致性靠其发布的**金样本**(golden fixtures)逐帧对表。`core/src/test/resources/fixtures/<v主.次>/` 是 pin 的物化(git 跟踪在库):**常规构建/测试零网络**。

当前 pin:v0.4(`handshake` 3 份 + `frames` 8 份 + `tolerance` 5 份 + `SHA256SUMS` 清单),来源 npm 包 `@kuro-bridge/protocol@0.4.0` = KuroProtocol tag `v0.4.0`。完整来源登记(npm 包版本 / 上游 commit / dist.integrity)见 `core/src/test/resources/fixtures/PIN.md`——刷新后须同步更新该表。

### 守卫三件套(`FixtureConformanceTest`,每次测试必跑)

1. **SUMS 逐文件哈希**:解析 `SHA256SUMS`,逐条校验文件存在 + SHA-256(`MessageDigest`)一致——内容被改动或拷贝失真即炸;
2. **集合一致性**:清单集合 == 目录下实际 `*.json` 集合(多出/缺失都报错);
3. **版本↔目录推导**:夹具目录名由 `ProtocolVersions.PROTOCOL_VERSION` 推导(`"0.4.0"` → `v0.4`),推导目录必须存在且就是消费目录——协议常量与夹具版本漂移即炸,不静默测旧目录。

### 逐帧消费与行为断言

- `@TestFactory` 动态发现目录内全部 JSON 逐份消费,按 `expect.schema`:
  - `accept` → 每帧按 `dir`(`peer->server` → `InboundFrames.validate`;`server->peer` → `OutboundFrames.validate`)先过 wire 骨架再过具体帧型校验,必须通过;
  - `reject` + `stage=wire` → `FrameCodec.parseWire` 必抛 `WireFormatException`;
  - `reject`(dispatch)→ 骨架通过但方向校验必抛 `FrameValidationException`。
- `expect.behavior.reply/close` 非空的样本额外跑**行为断言**:用 `PeerSession` + `FakeConnection` + `RecordingHooks` 跑首帧(非 hello 样本先内部握手),断言回执帧与金样本语义等价、close code/reason 精确一致、或无回执/不关闭。

## 金样本刷新(refreshFixtures)

显式任务 `:core:refreshFixtures`(mise run fixtures,需网络,**不挂入 build/check 依赖图**):

1. 从 `ProtocolVersions.java` 正则提取 `PROTOCOL_VERSION`,机械推导目录名 `v<主.次>` 与 npm tarball URL;
2. 下载解包(404 → 指路先在 KuroProtocol 发版并 npm publish;系统 tar 解包,不引解压依赖);
3. 校验包内 `SHA256SUMS` 存在 → **整体替换** `fixtures/<v主.次>/` 并删除其余陈旧 `v*` 目录(机械 bump 不留孤儿);
4. 任务输出提示后续手工步骤(更新 PIN.md 登记 → 跑 FixtureConformanceTest 验证 → 全量 build 后随协议 bump 同批提交)。

### 协议 bump 流程

KuroProtocol 发版并 npm publish → 同步本仓 `ProtocolVersions` 常量副本([wire-format.md](../protocol/wire-format.md))→ `refreshFixtures` 机械拉取 → 全量门禁(bump 在 PR diff 中逐字节可审)→ 更新 PIN.md 来源登记。
