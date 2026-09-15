plugins {
    id("spendlens.android.library")
    id("spendlens.android.hilt")
}

android {
    namespace = "com.spendlens.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlinx.coroutines.test)
}
