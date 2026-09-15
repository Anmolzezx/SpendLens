plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.spendlens.core.network"
}

dependencies {
    // `api`: the DTOs are @Serializable, and anything that encodes one needs the runtime too.
    api(libs.kotlinx.serialization.json)
}
