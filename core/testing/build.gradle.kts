plugins {
    id("spendlens.android.library")
}

android {
    namespace = "com.spendlens.core.testing"
}

dependencies {
    // Shared fakes and fixtures for other modules' tests. This is a main-source-set library on
    // purpose: a test artifact cannot be consumed by another module's test compilation.
    api(projects.core.data)
    api(projects.core.model)
    api(libs.kotlinx.coroutines.test)
}
