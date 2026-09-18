# KuroAdapter-Pure 现状（STATUS）

> 活文档：本文件跟踪当前进度、门禁结果与下一步。设计见 `docs/design.md`；
> :paper 接线设计见 `docs/history/PAPER-WIRING-2026-09-18.md`。

## 当前阶段：:paper 接线完成（事件 fan-out / 主线程调度 / 白名单与管理员命令）

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

## 门禁结果

- `mise exec java@25 -- ./gradlew build`：**全绿**（compile `-Xlint:all -Werror` + spotlessCheck + 全部测试）
- 测试：`:core` 共 7 个测试类 / **85 个用例**，0 失败 0 跳过（2026-09-18，自 78 增 7）：
  - PeerSessionTest 34 / FrameCodecTest 13 / ConfigLoaderTest 9 / PureWsServerTest 5（回环真机自测，
    含 broadcast 两条） / ConfigBindingStoreTest 5（新增）
  - VersionCompatTest 3 / **FixtureConformanceTest 16**（KuroProtocol v0.4 金样本逐份动态消费，
    含 7 份行为断言：握手三份 + 心跳/指令/查询往返 + 未知请求回执）

## 下一阶段清单

4. **真机联调**：Paper 服务器 + napukettoqq external 对端（或 koishi-plugin-kurobridge）
   打通握手/聊天/指令全链路（另线负责）；
5. **配置落盘**：首次启动生成默认 config.json + `/kurobridge reload` 命令（含热重载触发
   bindings_updated 推送、admins/绑定表刷新、serverId 配置化）。

## 已知边界与残留

- 不接真机（`:paper` 已接线但未在 Paper 服务器上跑过——本轮所有验证是回环单测 + 编译门禁）；
- 不做多版本平台模块（fabric/velocity 预留位，见 settings.gradle.kts 注释）；
- 协议常量为人工同步副本——协议 bump 须同步 KuroProtocol 并重新 pin fixtures；
- **本轮新发现/新引入的残留**：
  - `paper/build.gradle.kts` 补了 `log4j-core` compileOnly（运行期 Paper 自带，主仓同款）——
    AGENTS.md 红线 2 的依赖白名单措辞未涵盖该项，需文档块补一笔；
  - `hello_ack.serverId` 是常量 `kurobridge-pure`（主仓同为常量），配置化随第 5 条；
  - `BukkitBusinessScheduler.dispatch` 对 disable 窗口的任务为 warn + 丢弃（回执由对端 10s
    超时兜底，见设计书 §3.1）；`PeerSession.helloTimer` 的跨线程竞态为 :core 既有良性边界
    （设计书 §1.1 记录，未修）；
  - 平台→游戏广播是 `Bukkit.broadcast` 全服一行纯文本（`<sender> content`），无前缀/颜色
    定制位（对齐主仓现行行为，差异化留未来）。
