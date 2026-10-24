# 构建与依赖

Gradle 9.7 多模块工程(`settings.gradle.kts`:`include("core", "paper")`),坐标 `com.kurobridge:kuroadapter-pure 0.1.0`。构建入口为 mise tasks(定义于 `mise.toml`),CI 走原生 gradlew,两边同一条任务图。

## 常用命令

```bash
mise run build      # 门禁:编译 + spotlessCheck + 全部测试(= CI ./gradlew build)
mise run format     # 格式化修复(spotlessApply,提交前跑)
mise run jar        # 确定性重打唯一产物:clean + :paper:shadowJar
mise run fixtures   # 金样本机械刷新(需网络,显式任务;见 testing.md)
```

mise 经 `mise exec java@25` 保证 JDK 25(全局 `java` 可能是 21,不可用);偶用原生命令:`mise exec java@25 -- ./gradlew <task>`。

## 工具链与苛刻度(根 build.gradle.kts 统一收敛)

- Java 工具链 **25**,字节码 `options.release = 21`(兼容 Paper 运行时);UTF-8;`-Xlint:all -Werror`。
- Spotless + palantirJavaFormat **2.71.0**(JDK 25 兼容需 >= 2.71.0)。
- 测试统一 JUnit Platform;根项目 jar 禁用(无源码,默认 jar 只会产出与 fat JAR 同名的空壳)。

## 产物

唯一可装产物:`paper/build/libs/kuroadapter-pure-0.1.0.jar`(shadowJar fat JAR,内含 `:core`;`:paper` 薄壳 jar 与根 jar 均已禁用)。manifest 写 Implementation-Title/Version;**排除 `org.slf4j:slf4j-api`**(Java-WebSocket 传递依赖,Paper 运行期自带)。

已知坑:build 目标下的 shadowJar 可能因 up-to-date 被跳过(jar mtime 不刷新)——需要确定性重打用 `mise run jar`(先 clean)。gradle 输出乱码时:`JAVA_TOOL_OPTIONS="-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"`。

## 依赖白名单

版本唯一锚 = `gradle/libs.versions.toml`(版本目录,消除双处手写 pin)。白名单之外零引入:

| 依赖 | 版本 | 范围 | 理由 |
| :--- | :--- | :--- | :--- |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.2 | `:core` implementation | JSON 编解码(帧解析 + 配置加载) |
| `org.java-websocket:Java-WebSocket` | 1.6.0 | `:core` implementation | WS 服务端实现;MIT 许可,零额外传递运行时 |
| `org.java-websocket:Java-WebSocket` | 同上 | `:paper` compileOnly | `:core` 的 implementation 不传递到 `:paper` 编译类路径,而 `PureWsServer extends WebSocketServer`(公开父类须可见);运行期经 `:core` 传递进 shadowJar |
| `io.papermc.paper:paper-api` | 1.21.4-R0.1-SNAPSHOT | `:paper` compileOnly | 插件 API,运行期由服务端提供 |
| `org.apache.logging.log4j:log4j-core` | 2.25.1 | `:paper` compileOnly | vanilla 命令输出捕获([paper-commands.md](../modules/paper-commands.md));运行期由 Paper 自带 |
| `org.junit:junit-bom` + jupiter + launcher | 5.11.0 | test | JUnit 5 |
| `com.diffplug.spotless` / `com.gradleup.shadow` | 7.0.2 / 9.0.0 | 构建插件 | 格式化 / fat JAR |

不引入 Netty/tyrus 等更重 WS 方案;Java-WebSocket 的 `Protocol`/`Draft_6455` 已满足子协议协商。

## CI(`.github/workflows/ci.yml`)

单 job `gate`(ubuntu-latest),push 到 `main` + 全部 PR 触发:checkout → setup-java(temurin 25)→ setup-gradle → `./gradlew build --console=plain`。

**零网络特性**:`build` 不触发 `refreshFixtures`,fixtures 全部 git 跟踪在库,CI 仅需 Maven Central 与 papermc 仓库可达(金样本机制见 [testing.md](testing.md))。
