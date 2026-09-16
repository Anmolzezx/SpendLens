import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// No Kotlin plugin: AGP 9 brings Kotlin with it, and applying it again fails on a duplicate extension.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    id("spendlens.quality")
}

android {
    namespace = "com.spendlens.benchmark"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark drives the app from outside and reads its traces; both need API 28 or newer.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The app under test. This module contains no app code of its own — it launches :app and watches.
    targetProjectPath = ":app"

    // Self-instrumenting: the test APK runs in its own process, so measurements are not distorted by
    // the instrumentation sharing a process with the app being measured.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // The emulator here is not a clean room; on a real device this is the only honest setting.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
