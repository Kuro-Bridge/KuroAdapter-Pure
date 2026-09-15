# 金样本 pin 记录

| 项目 | 值 |
|---|---|
| 来源仓 | KuroProtocol（`C:\Dev\MC-Ecosystem\KuroProtocol`） |
| 协议版本 | 0.4.0 |
| 拷贝日期 | 2026-09-15 |
| 目录 | `fixtures/v0.4/`（handshake 3 / frames 8 / tolerance 5，共 16 份） |

拷贝方式：整目录拷贝自 KuroProtocol 仓 `fixtures/v0.4/`（该目录只随协议版本新增，已发布
版本内不修改）。消费方：`com.kurobridge.pure.core.fixtures.FixtureConformanceTest`
（动态发现全部 JSON，pin 守卫断言 16 份齐全）。

**协议 bump 流程**：KuroProtocol 发新版本 → 同步本仓 `ProtocolVersions` 常量副本 →
重新整目录拷贝新版本夹具（保留旧版本目录并存亦可）→ 更新本表来源版本与日期。
