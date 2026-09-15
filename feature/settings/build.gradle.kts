plugins {
    id("spendlens.android.feature")
}

android {
    namespace = "com.spendlens.feature.settings"
}

dependencies {
    // Whether this device can use app lock at all.
    implementation(projects.core.applock)
}
