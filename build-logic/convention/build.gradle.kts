plugins { `kotlin-dsl` }

group = "com.spendlens.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
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
    }
}
