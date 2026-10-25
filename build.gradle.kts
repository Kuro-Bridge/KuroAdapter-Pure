// KuroAdapter-Pure 根构建配置：所有模块共享的公共配置（版本/插件/苛刻度）

plugins {
    java
    id("com.diffplug.spotless") version "7.0.2" apply false
    id("com.gradleup.shadow") version "9.0.0" apply false
}

allprojects {
    // 坐标单一持有：root 与 subprojects 同源（Gradle 的 group/version 不自动继承，
    // 此前根上一份 + subprojects 里一份各写各的，收敛于此一处）
    group = "com.kurobridge"
    version = "0.1.0"

    repositories {
        // Paper API（compileOnly）等
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

// 所有模块统一的 Java 工具链、编译苛刻度与 Spotless（对齐主仓 ADR-011/ADR-015）
subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        // 缺省 release 21：测试源集保持工具链现代语法（主仓同款取舍）；:core/:paper 的 main
        // 源集在各自 build.gradle.kts 覆盖为 release 8（ADR-038 对齐主仓 universal：1.12.2 ~
        // 26.3 单 jar，--release 同时是 Java 9+ SE API 误用的机器门禁）
        options.release = 21
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror")) // 苛刻度（ADR-011）
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // 苛刻度：Spotless(Palantir)——此前 :core/:paper 各持一份同款配置，收敛于此
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat("2.71.0") // JDK 25 兼容：>=2.71.0 才能用新版 javac 内部 API（spotless#2625）
            target("src/**/*.java")
        }
    }
}

// 产物唯一化：根项目无源码，默认 jar 只会产出同名空壳（与 :paper fat JAR 同名异实体）——禁用
tasks.jar { enabled = false }
