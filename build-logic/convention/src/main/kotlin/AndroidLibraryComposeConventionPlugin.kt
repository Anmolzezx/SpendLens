import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * An Android library that renders Compose UI.
 *
 * Builds on `spendlens.android.library` rather than repeating its config: applying one convention
 * plugin from another keeps the compileSdk/minSdk/Java-target decision in exactly one place.
 */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("spendlens.android.library")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                // Type-safe `libs.` accessors do not exist inside a convention plugin — it is
                // compiled before the project's build scripts — hence the string lookups.
                val bom = platform(libs.findLibrary("androidx-compose-bom").get())
                "implementation"(bom)
                "androidTestImplementation"(bom)

                "implementation"(libs.findLibrary("androidx-compose-ui").get())
                "implementation"(libs.findLibrary("androidx-compose-ui-graphics").get())
                "implementation"(libs.findLibrary("androidx-compose-material3").get())
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())

                // ui-tooling is debug-only: it carries the preview renderer and must never ship
                // in a release build.
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
