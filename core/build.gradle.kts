// :core —— 协议层（kurobridge-ws v0.4.0 服务端）+ 业务骨架
// 平台无关：零 Bukkit API，可独立 JUnit 测试
// 依赖：Jackson（帧/配置 JSON 编解码）+ Java-WebSocket（WS 服务端，MIT）

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
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
