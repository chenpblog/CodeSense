plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // OkHttp for HTTP communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // 注意: Kotlin Coroutines 已由 IntelliJ Platform 提供，不需要显式添加

    // Testing
    // Testing - JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("com.google.code.gson:gson:2.10.1")
    // IntelliJ Platform 的 PathClassLoader 加载自带的 kotlin-test（JUnit4版本）时需要 TestRule
    // 提供 JUnit 4 作为运行时依赖以满足此要求，实际测试走 JUnit 5 引擎
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "263.*"
        }
    }
    
    // 插件兼容性验证配置 (使用 ./gradlew verifyPlugin 执行)
    pluginVerification {
        ides {
            // 默认验证 gradle.properties 中配置的开发版本
            // 2025.3 起 IC/IU 合并为统一发行版，使用 IU 产品代码
            val ver = providers.gradleProperty("platformVersion").get()
            @Suppress("DEPRECATION")
            ide("IU-$ver")
        }
    }

    // 插件发布配置
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}

// IntelliJ Platform 插件通过 systemProperty() 注入 java.system.class.loader=PathClassLoader
// 该系统类加载器包含 IJ 自带的 JUnit 4，与 JUnit 5 引擎冲突
// 在 afterEvaluate 里（所有插件配置完毕后）从 systemProperties 中移除
afterEvaluate {
    tasks.named<Test>("test") {
        systemProperties.remove("java.system.class.loader")
        systemProperties.remove("idea.home.path")
        // 也过滤一遍 jvmArgs（双保险）
        val filtered = jvmArgs?.filter {
            !it.contains("PathClassLoader") &&
            !it.contains("java.system.class.loader")
        } ?: emptyList()
        setJvmArgs(filtered)
    }
}


