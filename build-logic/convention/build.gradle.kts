import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { `kotlin-dsl` }

group = "com.spendlens.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // Must match the `java` block above. Without this, Kotlin defaults to the JDK running
        // Gradle (25 here) while Java compiles to 17, and Gradle warns about the mismatch.
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "spendlens.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "spendlens.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("jvmLibrary") {
            id = "spendlens.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "spendlens.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "spendlens.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("quality") {
            id = "spendlens.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
