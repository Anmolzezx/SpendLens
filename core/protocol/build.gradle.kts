plugins {
    id("spendlens.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

// The sync protocol's wire types, in plain Kotlin with no Android dependency, so the app and the Ktor
// server compile against the same classes. A field renamed on one side is a compile error on the
// other, not a production incident.
dependencies {
    api(libs.kotlinx.serialization.json)
}
