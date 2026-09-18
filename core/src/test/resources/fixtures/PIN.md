# 金样本 pin 记录

## 机制：npm 包机械刷新（2026-09-18 起）

fixtures 不再手工拷贝。`:core` 提供 **`refreshFixtures`** 任务（显式调用，不挂入
build/check 依赖图）：从 `ProtocolVersions.PROTOCOL_VERSION` 正则提取协议版本 →
机械推导 `v<主.次>` 目录名与 npm registry tarball URL → 下载解包 → **整体替换**
`core/src/test/resources/fixtures/<v主.次>/`（JSON + SHA256SUMS），并删除其余陈旧
`v*` 目录（机械 bump 不留孤儿）。新版本未发布时任务报 404 并指路：先在 KuroProtocol
发版并 npm publish，再 bump 常量。

**仓内 fixtures 是 pin 的物化**：git 跟踪在库，常规构建/测试零网络、离线构建不受影响；
刷新属显式动作、需网络。刷新结果不靠本表保证——由下方常驻守卫裁决。

## 守卫三件套（单一权威，常驻）

全部落在 `com.kurobridge.pure.core.fixtures.FixtureConformanceTest`，每次测试必跑：

1. **SUMS 逐文件哈希**：解析 `SHA256SUMS`，逐条校验文件存在 + SHA-256（MessageDigest）一致；
2. **集合一致性**：SUMS 清单集合 == 目录下实际 `*.json` 集合（多出/缺失都报错）——
   旧"固定 16 份"份数守卫由此升级为内容守卫；
3. **版本↔目录推导**：夹具目录名由 `ProtocolVersions.PROTOCOL_VERSION` 推导
   （"0.4.0" → "v0.4"），推导目录必须存在且就是测试消费的目录（协议常量与夹具版本
   漂移即炸，不静默测旧目录）。

## 来源登记（当前 pin）

| 项目 | 值 |
|---|---|
| 来源 | npm 包 `@kuro-bridge/protocol@0.4.0`（KuroProtocol ADR-003：金样本随主包分发） |
| 上游 tag | `v0.4.0` = KuroProtocol commit `09a1131` |
| tarball URL | `https://registry.npmjs.org/@kuro-bridge/protocol/-/protocol-0.4.0.tgz` |
| dist.integrity | `sha512-bvEB4yuTu+Qr2w3MscdPQ1KYxlJ8dOW7eBPTuKgr7QN4BVcZmVTJHDFePe2XQLyBfziQMtIennf8RrsJyg+8yA==` |
| 协议版本 | 0.4.0 |
| 目录 | `fixtures/v0.4/`（handshake 3 / frames 8 / tolerance 5，共 16 份 JSON + SHA256SUMS） |
| 本表更新 | 2026-09-18：机制由 KuroProtocol 仓手拷改为 npm 包机械刷新（docs/history/FIXTURES-CONSUMER-2026-09-18.md，候选 b）。npm 包内 fixtures/v0.4 与原手拷件字节级一致已验证（含 SHA256SUMS 本体）；历史手拷来源：JSON commit `8a5b328`、SUMS commit `a7daa40` |

## runbook

```bash
mise exec java@25 -- ./gradlew refreshFixtures                        # 机械刷新（需网络）
mise exec java@25 -- ./gradlew test --tests "*FixtureConformanceTest*" # 守卫三件套裁决刷新结果
mise exec java@25 -- ./gradlew build                                   # 全量门禁
```

## 协议 bump 流程

KuroProtocol 发新版本 → npm publish → 同步本仓 `ProtocolVersions` 常量副本 →
`./gradlew refreshFixtures`（机械拉取新包并整体替换目录）→ 全量门禁 `./gradlew build`
（守卫三件套即时验证刷新完整性，协议 bump 在 PR diff 中逐字节可审）→ 更新本表来源登记行。
