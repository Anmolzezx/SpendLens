dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // ktlint-gradle publishes only to the plugin portal, not Maven Central.
        gradlePluginPortal()
    }
    versionCatalogs {
        // Without this, `libs.` accessors don't exist inside the included build.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
include(":convention")
