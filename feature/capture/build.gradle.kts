plugins {
    id("spendlens.android.feature")
}

android {
    namespace = "com.spendlens.feature.capture"
}

dependencies {
    // `api`, not `implementation`: ParsedReceipt appears in captureScreen()'s public
    // signature, so :app needs the type on its compile classpath to wire the callback.
    api(projects.core.ocr)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)

    // Bundled model, not the Play Services one: it works offline on first launch with no download,
    // which is the whole premise of the app. The cost is roughly 4MB of APK.
    implementation(libs.mlkit.text.recognition)
}
