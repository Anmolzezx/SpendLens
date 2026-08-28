pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// Lets modules depend on each other as `projects.core.designsystem` instead of the stringly-typed
// project(":core:designsystem") — typos become compile errors and the IDE can autocomplete them.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpendLens"
include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
