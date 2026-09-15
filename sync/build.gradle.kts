plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.sync"

    defaultConfig {
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.data)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    // Generates the factory that lets WorkManager construct an @HiltWorker with injected dependencies.
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
