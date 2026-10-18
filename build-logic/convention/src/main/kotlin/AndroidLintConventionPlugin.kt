import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Android Lint for app, library and plain JVM modules: fatal rules from `REQUIREMENTS.md` §7, and
 * every other warning fails the build too (CLAUDE.md: no new lint warnings). No baseline.
 */
class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") -> {
                    extensions.configure<ApplicationExtension> {
                        lint {
                            configure()
                            // Also analyze the library modules the app depends on, as one project.
                            checkDependencies = true
                        }
                    }
                }

                pluginManager.hasPlugin("com.android.library") -> {
                    extensions.configure<LibraryExtension> { lint(Lint::configure) }
                }

                else -> {
                    apply(plugin = "com.android.lint")
                    extensions.configure<Lint>(Lint::configure)
                }
            }
        }
    }
}

private fun Lint.configure() {
    abortOnError = true
    warningsAsErrors = true
    fatal += listOf("NewApi", "UnusedResources", "MissingTranslation")
    disable +=
        listOf(
            // Dependency and AGP updates go through Dependabot and `./gradlew dependencyUpdates`, so a new
            // release must not break the build.
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            // TEMPORARY: the owner supplies the launcher icon (ROADMAP M0). Remove this line when it lands.
            "MissingApplicationIcon",
        )
}
