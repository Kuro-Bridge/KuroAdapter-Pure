# KuroAdapter-Pure 现状（STATUS）

> 活文档：本文件跟踪当前进度、门禁结果与下一步。设计见 `docs/design.md`；
> :paper 接线设计见 `docs/history/PAPER-WIRING-2026-09-18.md`；
> 配置落盘与安全默认裁决见 `docs/history/CONFIG-2026-09-18.md`。

## 当前阶段：配置落盘与安全默认 + 真机覆盖矩阵补齐完成

- [x] `docs/design.md` 设计先行（分层 / 协议层类结构 / 线程模型 / 差异 / 依赖白名单）
- [x] gradle 多模块骨架（`:core` / `:paper`，wrapper 9.7.0，spotless + shadow 约定）
- [x] `:core` 协议层：帧编解码 / 两段式解析 / 握手状态机 / 版本协商 / 鉴权 / 心跳空闲 /
      未知帧容忍 / PureWsServer（子协议协商 + 动态端口）
- [x] `:core` 业务骨架：ConfigLoader（去 runtime/embedded 段）+ BindingStore /
      ForwardRules / WhitelistGateway 接口空实现
- [x] 金样本夹具接线（pin KuroProtocol v0.4，16 份全部消费）
- [x] `:paper` 插件壳（paper-plugin.yml + 主类空壳，无事件接线）
- [x] **:paper 接线（2026-09-18，PAPER-WIRING 设计书先行）**：
      - [x] 1. Bukkit 事件接线：chat/join/quit/death 逐绑定频道 fan-out + status 快照推送
            （:core 补 `PureWsServer.broadcast(Frame):int` + `PeerSession.state` volatile）
      - [x] 2. BukkitBusinessScheduler 主线程投递（替换直执行）+ onPlatformChat
            绑定过滤 + `<sender> content` 全服广播
      - [x] 3. onCommand 全路径（管理员判定先于执行、无命令白名单——对齐主仓 ADR-027）+
            ConfigBindingStore（replace 变化才通知）+ PaperWhitelistGateway
- [x] BindingStore 真实现落地（ConfigBindingStore；变更来源 = 热重载，随第 5 条）
- [x] **配置落盘与安全默认（2026-09-18，CONFIG 决策书先行，D1-D8）**：
      - [x] 首启生成安全默认 config.json（channels []/随机 32 hex token/admins []/server.id/
            ws 127.0.0.1:25580；CREATE_NEW 防覆盖；生成后经 ConfigLoader 单一权威解析——:core
            纯类 ConfigBootstrap + @TempDir 单测）
      - [x] 安全默认 = 空 token 拒绝启动 WS 监听（对主仓「WARN 不阻断」有意偏差，指路文案含
            文件路径 + 设非空 + 对端须一致 + 本机调试任意非空串）
      - [x] serverId 配置化：可选根字段 `server.id`（缺省 `kurobridge-pure`；空串/非串/server
            非对象抛 ConfigException），进程固定
      - [x] `/kurobridge reload`：Lifecycle/Brigadier（BasicCommand）注册（paper-plugin.yml
            在 1.21.4 不支持 commands 块）；解析失败保留旧配置回显原因；成功则
            channels.replace（变化自动 bindings_updated 广播）+ AdminTable 重建原子换入 relay
            （volatile）；回执按 D6 热冷矩阵分组（token/ws/server.id 待重启，仅列有变化项）
            ——:core ConfigReloadReport 纯函数机械对比
      - [x] 端口绑定失败即时暴露（PureWsServer.startAndWait 轮询绑定期 onError，不再空等 10s
            超时）→ SEVERE 指路改 ws.port 后中止启用

## 门禁结果

- `mise exec java@25 -- ./gradlew build`：**全绿**（compile `-Xlint:all -Werror` + spotlessCheck + 全部测试）
- 测试：`:core` 共 10 个测试类 / **103 个用例**，0 失败 0 跳过（2026-09-19，自 99 增 4：
  StatusCountsTest——quit 窗口剔除/不在列表不重复剔除/仅退出者回零/空列表回零；
  `--no-build-cache` 实跑全绿，防 FROM-CACHE 掩盖）：
  - PeerSessionTest 34 / FrameCodecTest 13 / ConfigLoaderTest 11（+2：server.id 矩阵）/
    PureWsServerTest 6（+1：端口占用即时抛绑定失败）/ ConfigBindingStoreTest 5
  - VersionCompatTest 3 / **FixtureConformanceTest 17**（KuroProtocol v0.4 金样本 16 份逐份动态
    消费 + 1 条 SHA256SUMS 内容级 pin——逐文件哈希/清单与目录集合一致/版本↔目录推导；
    行为断言 7 份：握手三份 + 心跳/指令/查询往返 + 未知请求回执）
  - **ConfigBootstrapTest 6**（新增：生成形状 round-trip/token 32hex 且两次不同/既有文件
    字节不动/父目录创建/目录创建失败指路/token 门禁矩阵）
  - **ConfigReloadReportTest 4**（新增：无变化/channels+admins 热更组/token+server.id 冷组/
    ws 缺省→显式逐字段报）
  - **StatusCountsTest 4**（新增 2026-09-19：G1a 修复配套，见 GAPS-2026-09-19 §2.1）

## 下一阶段清单

4. **真机联调**（另线负责）：
   - [x] 真机冒烟（2026-09-18，`docs/history/SMOKE-2026-09-18.md`）：Paper 1.21.4 sandbox +
     koishi-dev external 对端——插件加载/WS 监听/子协议+token 握手/游戏→平台
     （join+chat+leave+status）/平台→游戏（chat→主线程广播）双侧日志实锤，二轮启动与
     优雅停用正常；平台→游戏的 koishi mock 注入路径被 koishi.db 既有数据阻断
     （UNIQUE 冲突，M7 已知），以协议合规 raw WS 客户端补证 Pure 侧全链；
   - [x] 指令全链路与 koishi external 端到端（2026-09-18/19，
     `docs/history/SMOKE-2026-09-18-2.md`）：矩阵 a–j 十格全过——command 往返（vanilla 回退 +
     Bukkit 收集 + 非管理员 forbidden）/ query / death / negate 静音（LuckPerms false→静默→
     true 恢复）/ bindings_updated（随 reload 尾格）/ koishi 真实 platform→game
     （koishi.db mock 行先备份后清理解锁，备份在 koishi-dev/data/backup-line2-20260918/）；
     尾格随块 A 合并态 jar 实证首启自生成 + 空 token 拒绝监听 + reload 热冷分组回执。
5. **配置落盘**：[x] 已完成（2026-09-18，见上方「配置落盘与安全默认」勾组；README 配置段
   为用户面文档，热冷矩阵与首启行为以 `docs/history/CONFIG-2026-09-18.md` 裁决为准）。
6. **待真机清单**（顺延下一波冒烟，本轮不抢沙盒；GAPS-2026-09-19 §5）：G1a 复验——
   两假人先后 quit，逐帧核对 status 帧 onlinePlayers 递减到位（SMOKE-2 §6-3 同场景；
   单测只保口径规则，Bukkit 事件接线须真机背书）。G1b/G2/G3 行为未变无需复验。

## 已知边界与残留

- 真机覆盖范围：冒烟（SMOKE-2026-09-18）+ 矩阵补格（SMOKE-2026-09-18-2，a–j 十格全过）——
  握手/双向 chat/command（含 vanilla 回退与 forbidden）/query/death/negate/bindings_updated/
  reload/首启自生成/空 token 拒绝均已真机实锤；残留为语义级边界（下两条）；
- status 快照语义：quit 计数偏大**已修**（2026-09-19，quit 路径按 UUID 剔除退出者，
  StatusCounts 口径 + 单测，GAPS-2026-09-19 §2.1；真机复验顺延见待真机清单）；
  `query status` 返回最近一帧缓存、事件稀疏场景可滞后——**对齐主仓的接受语义**（周期刷新
  已被主仓 ADR-034 否决；协议文本允许；GAPS §2.2）；全新起服无事件时正确回
  `no status yet`；
- `version` 命令仅捕获同步首行输出（异步余量直落控制台，收集窗口设计边界，主仓同构，
  **裁决不修**，GAPS §2.3）；非玩家聊天源（控制台 `say`）不转发为**显式非目标**——
  ChatListener 仅接 AsyncChatEvent，对齐主仓隐含语义升格为显式裁决（GAPS §2.4，
  SMOKE-2 §6-4/5；主仓 /kurobridge send 显式通道先例登记为未来增强）；
- 不做多版本平台模块（fabric/velocity 预留位，见 settings.gradle.kts 注释）；
- 协议常量为人工同步副本——协议 bump 须同步 KuroProtocol 并发 npm 版后跑
  `gradlew refreshFixtures` 从包内机械重取金样本（2026-09-18 起，手拷淘汰；见
  core/src/test/resources/fixtures/PIN.md 与 FIXTURES-CONSUMER-2026-09-18.md）；
- **本轮新发现/新引入的残留**：
  - `paper/build.gradle.kts` 补了 `log4j-core` compileOnly（运行期 Paper 自带，主仓同款）——
    AGENTS.md 红线 2 白名单已于 2026-09-18 补记该项（docs 块落地）；
  - `hello_ack.serverId` 已配置化（可选根字段 `server.id`，缺省 `kurobridge-pure`；进程固定，
    改后须重启——主仓同为进程固定语义）；
  - `BukkitBusinessScheduler.dispatch` 对 disable 窗口的任务为 warn + 丢弃（回执由对端 10s
    超时兜底，见设计书 §3.1）；`PeerSession.helloTimer` 的跨线程竞态为 :core 既有良性边界
    （设计书 §1.1 记录，未修）；
  - 平台→游戏广播是 `Bukkit.broadcast` 全服一行纯文本（`<sender> content`），无前缀/颜色
    定制位（对齐主仓现行行为，差异化留未来）。
