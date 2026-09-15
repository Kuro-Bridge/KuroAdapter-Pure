// KuroAdapter-Pure 多模块工程（root）
// :core —— 协议层 + 业务骨架（零 Bukkit API，可独立测试）；:paper —— Paper 适配薄壳
// 预留：未来可扩展 fabric / velocity 模块（复用 :core，只换适配层，对齐主仓 ADR-021）
rootProject.name = "kuroadapter-pure"

include("core", "paper")
