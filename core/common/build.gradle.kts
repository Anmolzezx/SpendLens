plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.core.common"
}

dependencies {
    // `api`: the Dispatcher qualifier appears in the public signature of anything that injects one.
    api(libs.kotlinx.coroutines.android)
}
