# 金样本 pin 记录

| 项目 | 值 |
|---|---|
| 来源仓 | KuroProtocol（`C:\Dev\MC-Ecosystem\KuroProtocol`，只读） |
| 协议版本 | 0.4.0 |
| 目录 | `fixtures/v0.4/`（handshake 3 / frames 8 / tolerance 5，共 16 份 JSON + SHA256SUMS） |
| JSON 内容来源线 | KuroProtocol commit `8a5b328`（2026-09-16 00:04:35 +0800，16 份金样本入库） |
| SHA256SUMS 来源线 | KuroProtocol commit `a7daa40`（2026-09-16 23:08:37 +0800，增设内容校验锚点，ADR-001） |
| 首次拷贝日期 | 2026-09-16（勘误：本表曾误记 2026-09-15，与 JSON mtime 及上述提交时间矛盾） |
| 本次更新 | 2026-09-18：补拷 SHA256SUMS 原样件 + 门禁升级为内容级校验（见下） |

拷贝方式：整目录拷贝自 KuroProtocol 仓 `fixtures/v0.4/`（该目录只随协议版本新增，已发布
版本内不修改）。拷贝件内容不靠本表人工维护，由门禁保证——**三件套全部落在
`com.kurobridge.pure.core.fixtures.FixtureConformanceTest`**：

1. **SUMS 逐文件哈希**：解析 `SHA256SUMS`，逐条校验文件存在 + SHA-256（MessageDigest）一致；
2. **集合一致性**：SUMS 清单集合 == 目录下实际 `*.json` 集合（多出/缺失都报错）——
   旧"固定 16 份"份数守卫由此升级为内容守卫；
3. **版本↔目录推导**：夹具目录名由 `ProtocolVersions.PROTOCOL_VERSION` 推导
   （"0.4.0" → "v0.4"），推导目录必须存在且就是测试消费的目录（协议常量与夹具版本
   漂移即炸，不静默测旧目录）。

**协议 bump 流程**：KuroProtocol 发新版本 → 同步本仓 `ProtocolVersions` 常量副本 →
重新整目录拷贝新版本夹具 + SHA256SUMS（含 SUMS 的门禁会即时验证拷贝完整性）→ 更新本表。

**远期路径（待办，不在本线实施）**：按 KuroProtocol ADR-003，改为从已发布的 npm 包消费
fixtures（`@kuro-bridge/protocol` 0.4.0 已上架），届时消灭手拷与本表。
