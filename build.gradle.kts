plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
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
            untilBuild = "251.*"
        }
    }
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
}
