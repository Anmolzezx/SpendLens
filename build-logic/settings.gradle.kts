dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        // Without this, `libs.` accessors don't exist inside the included build.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
include(":convention")
