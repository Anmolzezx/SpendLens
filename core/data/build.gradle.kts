plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.core.data"

    defaultConfig {
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)

    testImplementation(projects.core.database)
    testImplementation(libs.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
