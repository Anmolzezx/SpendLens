plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.core.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Defaults reach `./gradlew :server:run` on the development machine, through
        // `adb reverse tcp:8080 tcp:8080`, which forwards the device's localhost:8080 to it.
        // Not the emulator's 10.0.2.2 host alias: apps route over the emulator's Wi-Fi, which on current
        // images cannot reach it (only the shell's mobile route can), and it never works on a real
        // phone. `adb reverse` works for both. Override both values in ~/.gradle/gradle.properties
        // (never in this repository) to point a build at a deployed server.
        val baseUrl = providers.gradleProperty("spendlens.syncBaseUrl").getOrElse("http://localhost:8080/")
        val token = providers.gradleProperty("spendlens.syncToken").getOrElse("dev-token")
        buildConfigField("String", "SYNC_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "SYNC_TOKEN", "\"$token\"")
    }
}

dependencies {
    api(projects.core.protocol)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    testImplementation(projects.core.testing)
    testImplementation(projects.server)
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.kotlinx.coroutines.test)
}
