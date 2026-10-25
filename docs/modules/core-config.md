# 配置体系

配置文件为 **JSONC**:`plugins/KuroBridgePure/config.jsonc`(JSON 超集,支持行注释、块注释与行尾逗号,VS Code 原生识别;对齐主仓 ADR-040,解析机关为 Jackson `JsonReadFeature`,非自写剥离器)。插件数据目录名即插件名;无 YAML 配置。旧版 `plugins/KuroBridgePure/config.json` 仅回退读取(JSONC 解析器天然兼容),不迁移不重命名;`config.jsonc` 优先,两者皆缺才生成。逻辑位于 `:core` 的 `com.kurobridge.pure.core.business` 包(纯 Java,零 Bukkit API):`PureConfig`(形状)、`ConfigLoader`(解析)、`ConfigBootstrap`(首启注释模板 + 路径解析 + token 门禁)、`ConfigBindingStore`(绑定存储)、`ConfigReloadReport`(热重载对比)。

## 文件形状与逐字段语义

首启生成的注释模板(节选,注释即文档):

```jsonc
// KuroAdapter-Pure 配置文件(JSONC:支持行注释、块注释与行尾逗号)
// 可热更字段只有 channels 与 admins(/kurobridge reload),其余字段改后需重启。
{
  // 绑定频道列表(必填,可为空数组)
  "channels": [],
  // WS 握手鉴权 token(首启随机生成,对端须同配)
  "token": "<32 位随机 hex>",
  "admins": [],
  "server": { "id": "kurobridge-pure" },
  "ws": { "host": "127.0.0.1", "port": 25580 }
}
```

下表为去掉注释后的等义形状:

```json
{
  "channels": [],
  "token": "<32 位随机 hex>",
  "admins": [],
  "server": { "id": "kurobridge-pure" },
  "ws": { "host": "127.0.0.1", "port": 25580 }
}
```

| 字段 | 缺省(不写时) | 解析语义(`ConfigLoader`) | 热重载 |
| :--- | :--- | :--- | :--- |
| `channels` | 无(**必填**,可为空数组) | 字符串数组,元素非空,**去重保序** | 热更 |
| `token` | `""` | 字符串;缺省空串 = 不鉴权(解析层向后兼容,但启动受门禁,见下) | 冷 |
| `admins` | `[]` | `{channel, users[]}` 数组;channel 与 users 各自去重保序;channel 重复保留首个;缺省 = 无人可经 command 执行 | 热更 |
| `server.id` | `kurobridge-pure` | 非空字符串;`server` 段非对象 / id 非串 / 空串均拒绝;`hello_ack` 上报用,进程固定 | 冷 |
| `ws.host` | 全部接口 | 非空字符串 | 冷 |
| `ws.port` | 动态端口 | 1-65535 的**数学整数**(`25580.5` 非法、`25580.0` 合法,对齐 zod 整数语义) | 冷 |

- **JSONC 语法**:行注释 `//`、块注释 `/* */`、行尾逗号均合法;注释不参与解析,未闭合注释/非法 JSON 照常抛 `ConfigException`。
- **路径解析**(`ConfigBootstrap.resolveConfigFile`):`config.jsonc` 优先;缺失且存在旧版 `config.json` 时回退读取(启动日志提示改名以获得注释模板);两者皆缺返回 `config.jsonc` 作为首启生成目标。启用期与 reload 共用同一解析,不制造双权威。
- `ws` 整段缺省 = 全部接口 + 动态端口;只配 `host` 不配 `port` 合法。
- 根、段上的多余字段一律剥离;任何形状/内容非法抛类型化 `ConfigException`(不静默吞),`:paper` 启用期收到即 SEVERE 中止启用。
- 无 runtime / embedded 配置段(纯 Java 线无子进程/无嵌入,有意剔除,对齐主仓 schema 其余形状)。

## 首启引导(`ConfigBootstrap`)

- **生成**(`ensureConfigFile`):文件缺失时生成注释模板并落盘(父目录一并创建;模板形状与主仓 `defaultConfigJsonc` 同文体,字段按本线裁剪)。写入用 `CREATE_NEW` 语义防覆盖;并发竞争落败(别处已生成)静默退回,照常走 `ConfigLoader` 加载——**生成件不设第二解析路径**,`ConfigLoader` 是单一权威。目录创建失败 / IO 失败抛 `ConfigException` 指路。生成仅发生在启用期;运行期 reload 时文件缺失**不**重新生成。
- **随机 token**:16 字节 `SecureRandom` → 32 位小写 hex,拼入模板 `token` 字段,只落盘不回显(日志只指路文件路径)。
- **token 门禁**(`requireToken`,WS 监听启动前):token 为空(缺失或 `""`)→ `ConfigException`,文案含文件路径 + 指路(设非空令牌;对端须同配;仅本机调试可用任意非空串)。这是对主仓"WARN 不阻断"的**有意安全偏差**。

## 绑定存储(`ConfigBindingStore`)

`BindingStore` 接口(`boundChannels()` / `isBound()` / `addChangeListener()`)的配置驱动实现,SSOT = 服主配置的 `channels`:

- `channels` 为 volatile(整表替换可能在主线程外触发);监听器持于 `CopyOnWriteArrayList`。
- `replace(next)`:集合相同 = no-op 返回 false;变化则替换并**在替换线程上同步**通知全部监听("变化才推"语义)——`:paper` 将监听接到 `bindings_updated` 广播(见 [paper-plugin.md](paper-plugin.md))。

## 热重载对比(`ConfigReloadReport`)

`ConfigReloadReport.diff(previous, next)` 是 reload 回执的唯一事实来源(机械对比,纯函数):

| 分组 | 字段 | 生效方式 |
| :--- | :--- | :--- |
| `hotApplied`(已热更) | `channels`(变化即自动 `bindings_updated` 广播)、`admins`(管理员表重建原子换入) | 立即生效 |
| `restartPending`(待重启) | `token`、`ws.host`、`ws.port`、`server.id` | 进程固定,重启生效 |

`ws` 整段缺省按 host/port 均 null 参与对比;回执只列有变化项。reload 命令的注册与执行流程见 [paper-commands.md](paper-commands.md)。
