plugins {
    id("spendlens.android.library.compose")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.core.applock"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    // `api`: UnlockPrompt takes a FragmentActivity, which comes with it.
    api(libs.androidx.biometric)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
