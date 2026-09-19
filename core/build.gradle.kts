// :core —— 协议层（kurobridge-ws v0.4.0 服务端）+ 业务骨架
// 平台无关：零 Bukkit API，可独立 JUnit 测试
// 依赖：Jackson（帧/配置 JSON 编解码）+ Java-WebSocket（WS 服务端，MIT）

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.java.websocket)

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 苛刻度：Spotless(Palantir)（对齐主仓 ADR-011）
spotless {
    java {
        palantirJavaFormat("2.71.0") // JDK 25 兼容：>=2.71.0 才能用新版 javac 内部 API（spotless#2625）
        target("src/**/*.java")
    }
}

// ---------------------------------------------------------------------------
// 金样本机械刷新（docs/history/FIXTURES-CONSUMER-2026-09-18.md 裁决：候选 b）
// 链路：ProtocolVersions 常量 → npm registry tarball → 系统 tar 解包 → 整体替换
// fixtures/<v主.次> + 清除陈旧 v* 目录 → 刷新结果由 FixtureConformanceTest（守卫单一权威）裁决。
// 一次性显式任务：不挂入 build/check 依赖图——常规构建/测试零网络，fixtures 跟踪在库。
val protocolVersionsSource =
    layout.projectDirectory.file(
        "src/main/java/com/kurobridge/pure/core/protocol/ProtocolVersions.java")
val testFixturesDir = layout.projectDirectory.dir("src/test/resources/fixtures")
val fixturesPkgDir = layout.buildDirectory.dir("fixtures-pkg")

tasks.register("refreshFixtures") {
    group = "kurobridge"
    description =
        "从 npm 包 @kuro-bridge/protocol 机械刷新金样本 fixtures（显式调用；版本取自 ProtocolVersions.PROTOCOL_VERSION，需网络）"

    doLast {
        // 1) 正则提取协议版本 → 机械推导目录名（0.4.0 → v0.4）与 tarball URL（常量改，目标同步变）
        val sourceFile = protocolVersionsSource.asFile
        val version =
            Regex("\\bPROTOCOL_VERSION\\s*=\\s*\"([^\"]+)\"")
                .find(sourceFile.readText())
                ?.groupValues
                ?.get(1)
                ?: throw GradleException(
                    "未能从 " +
                        sourceFile.relativeTo(layout.projectDirectory.asFile).path +
                        " 提取 PROTOCOL_VERSION——常量声明格式漂移，请人工核对")
        val lastDot = version.lastIndexOf('.')
        if (lastDot <= 0) {
            throw GradleException("PROTOCOL_VERSION=\"$version\" 缺少主.次段，无法推导 fixtures 目录名")
        }
        val fixturesDirName = "v" + version.substring(0, lastDot)
        val tarballUrl = "https://registry.npmjs.org/@kuro-bridge/protocol/-/protocol-$version.tgz"
        logger.lifecycle("refreshFixtures：PROTOCOL_VERSION=$version → $fixturesDirName ← $tarballUrl")

        // 2) HttpClient 下载 tarball（404 → 指路：先在 KuroProtocol 发版并 npm publish，再 bump 常量）
        val pkgDir = fixturesPkgDir.get().asFile
        pkgDir.deleteRecursively()
        pkgDir.mkdirs()
        val tarball = pkgDir.resolve("protocol-$version.tgz")
        val response =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                .send(
                    HttpRequest.newBuilder(URI.create(tarballUrl))
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() == 404) {
            throw GradleException(
                "npm registry 404：$tarballUrl 不存在——@kuro-bridge/protocol@$version 尚未发布。" +
                    "指路：先在 KuroProtocol 发版并 npm publish，再 bump ProtocolVersions 常量。")
        }
        if (response.statusCode() != 200) {
            throw GradleException("npm registry 下载失败（HTTP ${response.statusCode()}）：$tarballUrl")
        }
        tarball.writeBytes(response.body())

        // 3) 系统 tar 解包（Win10+ 自带 bsdtar，类 Unix 自带；不引解压依赖）
        val unpacked = pkgDir.resolve("unpacked")
        unpacked.mkdirs()
        val tar =
            ProcessBuilder("tar", "-xzf", tarball.absolutePath, "-C", unpacked.absolutePath)
                .redirectErrorStream(true)
                .start()
        val tarOutput = tar.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        if (!tar.waitFor(2, TimeUnit.MINUTES)) {
            tar.destroyForcibly()
            throw GradleException("tar 解包超时（120s）：$tarball")
        }
        if (tar.exitValue() != 0) {
            throw GradleException("tar 解包失败（exit=${tar.exitValue()}）$tarOutput：$tarball")
        }

        // 4) 校验解包件：package/fixtures/<v主.次>/SHA256SUMS 必须存在
        val pkgFixturesDir = unpacked.resolve("package/fixtures/$fixturesDirName")
        if (!Files.isRegularFile(pkgFixturesDir.resolve("SHA256SUMS").toPath())) {
            throw GradleException(
                "npm 包内缺少 fixtures/$fixturesDirName/SHA256SUMS——包内容与协议版本 $version 不符。" +
                    "指路：核对 KuroProtocol 发布流水线是否已把 $version 的 fixtures 打进 npm 包。")
        }

        // 5) 整体替换 fixtures/<v主.次>（先删后拷），并清除其余陈旧 v* 目录（机械 bump 不留孤儿）
        val resourcesFixtures = testFixturesDir.asFile
        resourcesFixtures
            .listFiles { dir -> dir.isDirectory && Regex("^v\\d+\\.\\d+$").matches(dir.name) }
            ?.sortedBy { it.name }
            ?.forEach { dir ->
                if (dir.name != fixturesDirName) {
                    logger.lifecycle("refreshFixtures：删除陈旧目录 ${dir.name}/")
                }
                dir.deleteRecursively()
            }
        pkgFixturesDir.copyRecursively(resourcesFixtures.resolve(fixturesDirName))

        // 6) 后续手工步骤提醒（刷新结果由 FixtureConformanceTest 守卫三件套裁决）
        logger.lifecycle(
            "refreshFixtures：已整体替换 src/test/resources/fixtures/$fixturesDirName/（JSON + SHA256SUMS）")
        logger.lifecycle("后续手工步骤：")
        logger.lifecycle(
            "  1. 更新 core/src/test/resources/fixtures/PIN.md 的来源登记行（npm 包版本 / tag commit / dist.integrity）")
        logger.lifecycle(
            "  2. mise exec java@25 -- ./gradlew test --tests \"*FixtureConformanceTest*\" 验证刷新结果" +
                "（SUMS 逐文件 / 集合一致 / 版本↔目录推导）")
        logger.lifecycle("  3. mise exec java@25 -- ./gradlew build 全量门禁后，随协议 bump 同批提交")
    }
}
