import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

/**
 * A user-facing feature module: Compose UI plus the domain and design-system dependencies every
 * feature needs.
 *
 * Applying the serialization plugin here rather than in each module is what keeps the type-safe
 * navigation routes of §7 to a single `@Serializable` annotation at the call site.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("spendlens.android.library.compose")
                apply("spendlens.android.hilt")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                // Every feature gets these two and nothing else from the project. Notably absent:
                // any other feature. Cross-feature navigation is resolved in :app by passing
                // lambdas down, which is what keeps features independently buildable.
                "implementation"(project(":core:model"))
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:data"))
                // Dispatcher qualifiers — a feature that does work off the main thread needs these.
                "implementation"(project(":core:common"))

                // A feature owns its own navigation routes and graph, so it needs the navigation
                // API. kotlinx-serialization-json is what type-safe routes encode arguments with.
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())
                "implementation"(libs.findLibrary("kotlinx-serialization-json").get())

                // The ViewModel seam: hiltViewModel() plus lifecycle-aware state collection.
                "implementation"(libs.findLibrary("hilt-navigation-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())

                "testImplementation"(project(":core:testing"))
                "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
                "testImplementation"(libs.findLibrary("turbine").get())
            }
        }
    }
}
