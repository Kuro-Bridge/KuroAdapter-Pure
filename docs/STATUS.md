# KuroAdapter-Pure 现状（STATUS）

> 活文档：本文件跟踪当前进度、门禁结果与下一步。设计见 `docs/design.md`。

## 当前阶段：bootstrap（协议层 + 业务骨架 + 夹具门禁）

- [x] `docs/design.md` 设计先行（分层 / 协议层类结构 / 线程模型 / 差异 / 依赖白名单）
- [x] gradle 多模块骨架（`:core` / `:paper`，wrapper 9.7.0，spotless + shadow 约定）
- [x] `:core` 协议层：帧编解码 / 两段式解析 / 握手状态机 / 版本协商 / 鉴权 / 心跳空闲 /
      未知帧容忍 / PureWsServer（子协议协商 + 动态端口）
- [x] `:core` 业务骨架：ConfigLoader（去 runtime/embedded 段）+ BindingStore /
      ForwardRules / WhitelistGateway 接口空实现
- [x] 金样本夹具接线（pin KuroProtocol v0.4，16 份全部消费）
- [x] `:paper` 插件壳（paper-plugin.yml + 主类空壳，无事件接线）

## 门禁结果

- `mise exec java@25 -- ./gradlew build`：**全绿**（compile `-Xlint:all -Werror` + spotlessCheck + 全部测试）
- 测试：`:core` 共 6 个测试类 / **78 个用例**，0 失败 0 跳过：
  - PeerSessionTest 34 / FrameCodecTest 13 / ConfigLoaderTest 9 / PureWsServerTest 3（回环真机自测）
  - VersionCompatTest 3 / **FixtureConformanceTest 16**（KuroProtocol v0.4 金样本逐份动态消费，
    含 7 份行为断言：握手三份 + 心跳/指令/查询往返 + 未知请求回执）

## 下一阶段清单

1. ~~**Bukkit 事件接线**（`:paper`）：聊天/进退服/死亡事件 → 帧 fan-out（经 BindingStore）；
   status 事件驱动推送（玩家进出服时）~~ —— 完成（2026-09-18，见
   `docs/history/PAPER-WIRING-2026-09-18.md`；:core 补 `PureWsServer.broadcast`）；
2. **BusinessScheduler 的 Paper 实现**：投递 Bukkit 主线程（`BukkitScheduler.runTask`），
   替换直执行缺省；
3. **业务实现**：BindingStore 真实现（配置热重载 + bindings_updated 推送）、
   ForwardRules 转发规则、WhitelistGateway 接 `Bukkit.dispatchCommand`（CONSOLE 名义）、
   command 管理员判定（admins 配置）；
4. **真机联调**：Paper 服务器 + napukettoqq external 对端（或 koishi-plugin-kurobridge）
   打通握手/聊天/指令全链路；
5. **配置落盘**：首次启动生成默认 config.json + `/kurobridge reload` 命令。

## 已知边界（本阶段不做）

- 不做事件接线、不接真机（`:paper` 仅空壳 + `// 下一阶段` 标注）；
- 不做多版本平台模块（fabric/velocity 预留位，见 settings.gradle.kts 注释）；
- 协议常量为人工同步副本——协议 bump 须同步 KuroProtocol 并重新 pin fixtures。
