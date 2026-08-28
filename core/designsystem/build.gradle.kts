plugins {
    id("spendlens.android.library.compose")
}

android {
    namespace = "com.spendlens.core.designsystem"
}

dependencies {
    // `api`, not `implementation`: SemanticColors exposes ImmutableList in its public signature,
    // so every consumer needs the type on its compile classpath.
    api(libs.kotlinx.collections.immutable)
}
