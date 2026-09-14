plugins {
    id("spendlens.android.feature")
}

android {
    namespace = "com.spendlens.feature.expenses"
}

dependencies {
    // Receipt photos are loaded from internal storage. Coil decodes off the main thread and
    // downsamples to the view size, which matters: a camera capture is ~12MP, and decoding that at
    // full resolution into a 300dp-tall box would allocate tens of megabytes per scroll.
    implementation(libs.coil.compose)
}
