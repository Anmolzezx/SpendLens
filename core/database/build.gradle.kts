plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "com.spendlens.core.database"

    defaultConfig {
        // Robolectric needs a real Android runtime; the DAO tests run on the JVM in CI rather than
        // needing an emulator, so unit tests must see android resources.
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

room {
    // Exported schemas are the input to Room's automated migration tests, and reviewing the JSON
    // diff is how a schema change stops being invisible in a pull request. Commit this directory.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
