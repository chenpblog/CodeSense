plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
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
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("Git4Idea")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    // OkHttp for HTTP communication
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // 注意: Kotlin Coroutines 已由 IntelliJ Platform 提供，不需要显式添加

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "263.*"
        }
    }
    
    // 插件兼容性验证配置 (使用 ./gradlew verifyPlugin 执行)
    pluginVerification {
        ides {
            // 默认验证 gradle.properties 中配置的开发版本
            val type = providers.gradleProperty("platformType").getOrElse("IC")
            val ver = providers.gradleProperty("platformVersion").get()
            ide("$type-$ver")
            // 你也可以手动添加需要额外验证的特定版本，格式为：<类型>-<版本>
            // ide("IC-2024.1.4")
            // ide("IU-2024.2.3")
        }
    }

    // 插件发布配置
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
