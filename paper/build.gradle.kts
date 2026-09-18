// :paper —— Paper 服务端适配薄壳（插件生命周期；事件接线见下一阶段）
// 依赖 :core（协议层 + 业务骨架）+ Paper API（compileOnly）
// 产物：shadowJar fat JAR（内含 :core）

plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // :core 的 Java-WebSocket 是 implementation 不传递到本模块编译类路径，而 PureWsServer
    // extends WebSocketServer（公开类型的父类须可见）——显式声明同版本；运行期经 :core
    // 传递进 runtimeClasspath（shadowJar 照常打入 fat JAR，不重复）
    compileOnly("org.java-websocket:Java-WebSocket:1.6.0")
    // vanilla 命令输出捕获（PaperCommandDispatcher 的 vanilla 回退路径）：仅编译期需要，
    // 运行期由 Paper 服务端自带 log4j-core 提供（主仓同款声明；设计书 §4 白名单措辞待补）
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.1")
}

// 苛刻度：Spotless(Palantir)（对齐主仓 ADR-011）
spotless {
    java {
        palantirJavaFormat("2.71.0") // JDK 25 兼容：>=2.71.0 才能用新版 javac 内部 API（spotless#2625）
        target("src/**/*.java")
    }
}

// 产物唯一化：fat JAR 是唯一可装产物——禁用 Gradle 默认 jar（薄壳），保证 build 后 libs 只剩 fat JAR
tasks.jar { enabled = false }

tasks.shadowJar {
    archiveFileName.set("kuroadapter-pure-${project.version}.jar")
    archiveClassifier.set("")
    manifest {
        attributes("Implementation-Title" to "KuroAdapter-Pure", "Implementation-Version" to project.version)
    }
    // slf4j-api 是 Java-WebSocket 的传递依赖：Paper 运行期自带，不打进 fat JAR
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}
