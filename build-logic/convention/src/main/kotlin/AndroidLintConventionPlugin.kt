import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

/** Android Lint for app, library and plain JVM modules; fatal rules from `REQUIREMENTS.md` §7. */
class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            when {
                pluginManager.hasPlugin("com.android.application") -> {
                    extensions.configure<ApplicationExtension> { lint(Lint::configure) }
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
    fatal += listOf("NewApi", "UnusedResources", "MissingTranslation")
}
