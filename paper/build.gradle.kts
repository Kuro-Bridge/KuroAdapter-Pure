// :paper —— Paper 服务端适配薄壳（插件生命周期；事件接线见下一阶段）
// 依赖 :core（协议层 + 业务骨架）+ Paper API（compileOnly）
// 产物：shadowJar fat JAR（内含 :core）

plugins {
    id("com.gradleup.shadow")
}

// Java 8 字节码基线（对齐主仓 ADR-038 universal 兼容），同 :core：只约束 main 源集，
// --release 8 是 Java 9+ SE API 误用的机器门禁。
// 消费端 JVM 版本声明为 21 的原因：paper-api 1.21.4 元数据带 TARGET_JVM_VERSION 21，
// 与本模块 release 8 的编译目标不一致，须显式对齐元数据否则选择器直接拒绝解析。
import org.gradle.api.attributes.java.TargetJvmVersion

tasks.named<JavaCompile>("compileJava") {
    options.release = 8
    // -Xlint:-options 压制 JDK 25 的「源/目标 8 已过时」提示；-Xlint:-classfile 压制
    // release-8 类文件模型读 1.21.4 paper-api jar（@Deprecated(since=/forRemoval=) 为
    // Java 9+ 元素）的固有噪音——两者均非源码问题，-Werror 之外的真实门禁保留
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-Xlint:-classfile"))
}

configurations.named("compileClasspath") {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // :core 的 Java-WebSocket 是 implementation 不传递到本模块编译类路径，而 PureWsServer
    // extends WebSocketServer（公开类型的父类须可见）——显式声明同版本（版本目录单一 pin）；运行期经 :core
    // 传递进 runtimeClasspath（shadowJar 照常打入 fat JAR，不重复）
    compileOnly(libs.java.websocket)
    // vanilla 命令输出捕获（PaperCommandDispatcher 的 vanilla 回退路径）：仅编译期需要，
    // 运行期由 Paper 服务端自带 log4j-core 提供（主仓同款声明；设计书 §4 白名单措辞待补）
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.1")
}

// 产物唯一化：fat JAR 是唯一可装产物——禁用 Gradle 默认 jar（薄壳），保证 build 后 libs 只剩 fat JAR
tasks.jar { enabled = false }

tasks.shadowJar {
    archiveFileName.set("kuroadapter-pure-${project.version}.jar")
    archiveClassifier.set("")
    // Jackson 三件各带 META-INF/LICENSE/NOTICE，重复 zip 条目会被 Paper 1.20.5+ 的
    // PluginRemapper 拒绝（经典 plugin.yml 的 api-version 1.13 触发重映射；主仓 2026-10-25
    // Paper-1.21.11 实证 Duplicate entries detected）——EXCLUDE 保首个，条目唯一
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Implementation-Title" to "KuroAdapter-Pure", "Implementation-Version" to project.version)
    }
    // slf4j-api 是 Java-WebSocket 的传递依赖：Paper 运行期自带，不打进 fat JAR
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
    }
}
