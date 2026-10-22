import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/**
 * Android Lint for app, library and plain JVM modules: fatal rules from `REQUIREMENTS.md` §7 (`MissingTranslation`
 * informational since translations ship at 80%, `docs/adr/0019-translations-on-weblate.md`), and
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
    fatal += listOf("NewApi", "UnusedResources", "ExtraTranslation")
    // A language ships at 80% translated (docs/TRANSLATING.md): the strings it lacks show in English, so a missing
    // translation is reported, not fatal. Below 80% a language is not packaged, and Lint does not check it.
    informational += "MissingTranslation"
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
