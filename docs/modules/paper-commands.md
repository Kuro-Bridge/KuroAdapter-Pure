# 命令链路

本文覆盖两条命令链路:`/kurobridge reload`(服主本地管理命令)与 `command` 帧(平台侧来源的命令执行)。两者共用同一执行管线(`PaperCommandDispatcher`)。

## /kurobridge reload

- **注册**:`paper-plugin.yml` 在 Paper 1.21.4 不支持 commands 块(`PluginMeta` 无 commands 字段),故经 `LifecycleEvents.COMMANDS` + `BasicCommand`(Brigadier)注册;COMMANDS 事件在全部插件启用完成后触发,重注册幂等。命令描述"KuroBridge 互通桥管理",无别名。
- **权限**:`kurobridge.reload`(paper-plugin.yml 声明 default op;控制台恒可)。execute 由 Brigadier 在**主线程同步派发**。
- **行为**(`KurobridgeCommand.execute` → 插件主类 `performKurobridgeReload`):
  - 参数非 `["reload"]` → 回显 USAGE(命令现仅 reload 一个子命令);
  - 未启用守卫:插件未完成启用(配置非法或启动失败)→ 拒绝并说明;
  - 读盘重解析:`ConfigLoader.load` 失败 → **保留旧配置**,回显原因;文件缺失**不**重新生成(生成仅发生在启用期);
  - 成功 → `bindings.replace(next.channels)`(集合变化自动触发 `bindings_updated` 广播)→ admins 变化时重建 `AdminTable` 经 `relay.replaceAdmins` 原子换入(volatile)→ 按热冷矩阵回执(只列有变化项,见 [core-config.md](core-config.md))。

## command 帧:管理员判定

`PaperRelay.onCommand`(主线程,经 `BukkitBusinessScheduler` 派发):

1. `AdminTable.isAdmin(source.channel, source.userId)` 判定——不可变表(channel → users 集合),数据源配置 `admins`;热重载 = 重建实例原子换入;
2. 非管理员 → warn 日志 + 同步返回 `failure("forbidden")`,不执行;
3. 管理员 → 透传 `commandDispatcher.execute(command)`。**无命令白名单**——管理员即可执行任意命令;结果以已完成 future 返回,`whenComplete` 回帧也发生在主线程(线程安全出帧)。

`command.command` 不含前导斜杠是**对端约定**(peer-guide),本层不校验不剥除。

## 命令执行管线(`PaperCommandDispatcher.execute`)

线程契约:仅在 Bukkit 主线程调用。执行完命令才组结果(同步完成 = 对端拿到真实执行结果)。双路径输出收集:

1. **常规路径**:创建 `CollectingCommandSender`(收集型 sender,控制台语义:`hasPermission`/`isOp` 恒 true、名为 Console;adventure 各 `sendMessage(Component)` 变体最终汇入 `sendMessage(Identity, Component, MessageType)` 汇聚点)→ `Bukkit.dispatchCommand(sender, command)` → 收集回显行;
2. **vanilla 回退路径**:Paper 的 `VanillaCommandWrapper` 只认内部 Craft* sender,自定义 sender 执行 vanilla 命令(如 `whitelist`、`version`)时抛"Cannot make ... a vanilla command listener"——按异常链识别该拒绝(`isVanillaListenerRejection`)后**回退真实 console sender 重派**;其反馈经 log4j 控制台流,由 `VanillaFeedbackCapture` 在执行窗口内挂载的收集型 appender 截取(**只收 `Server thread` 线程、attach/detach 窗口内的日志行**,两条路径互斥不重复);
3. 输出取收集行;Bukkit 路径为空则用 capture 行;命令本身抛异常(插件命令 bug 等)→ warn + `failure("命令执行异常:...")`,避免对端等到超时。

已知边界:`version` 等命令的异步余量输出直落控制台、不进回执(收集窗口设计边界)——见 [limits.md](../architecture/limits.md)。

## 白名单网关(`PaperWhitelistGateway`)

`WhitelistGateway` 接口的 Paper 实现:`whitelist list` / `whitelist add <player>` / `whitelist remove <player>` 组命令串经 `PaperCommandDispatcher` 以 CONSOLE 名义执行并收集输出,与 command 帧透传路径复用同一执行机制;结果以已完成 future 返回(失败回 `IllegalStateException(error)`)。

**现状:结构化 API 无调用方**——command 帧直传 `whitelist ...` 字符串已达同效;本实现供未来结构化调用(如面板/上层 API)使用。白名单 SSOT = MC 原生 `whitelist.json`,本仓不自建存储。
