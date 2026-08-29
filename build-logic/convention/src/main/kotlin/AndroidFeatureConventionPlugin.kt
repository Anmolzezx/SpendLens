import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
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
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            dependencies {
                // Every feature gets these two and nothing else from the project. Notably absent:
                // any other feature. Cross-feature navigation is resolved in :app by passing
                // lambdas down, which is what keeps features independently buildable.
                "implementation"(project(":core:model"))
                "implementation"(project(":core:designsystem"))
            }
        }
    }
}
