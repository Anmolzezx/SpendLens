import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * Static analysis and formatting, applied to every module through the base convention plugins.
 *
 * Deliberately *not* `subprojects { apply(...) }` in the root build file: that couples every module's
 * configuration to the root project, forces configuration of projects that may not need it, and is
 * the thing convention plugins exist to replace.
 *
 * The two tools do different jobs and are configured not to overlap — ktlint owns formatting, detekt
 * owns complexity and correctness smells. detekt's `formatting` ruleset is left out precisely because
 * it wraps ktlint and would produce duplicate, sometimes contradictory, findings.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jlleitschuh.gradle.ktlint")
                apply("dev.detekt")
            }

            extensions.configure<KtlintExtension> {
                android.set(true)
                ignoreFailures.set(false)
                filter {
                    // Generated sources are not ours to format.
                    exclude { it.file.path.contains("/build/") }
                }
            }

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                parallel.set(true)
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                // Report paths relative to the repo root, so CI annotations line up with the diff.
                basePath.set(rootProject.layout.projectDirectory)
            }

            tasks.withType<Detekt>().configureEach {
                reports {
                    html.required.set(true)
                    // SARIF so GitHub can annotate a pull request inline.
                    sarif.required.set(true)
                    checkstyle.required.set(false)
                    markdown.required.set(false)
                }
            }
        }
    }
}
