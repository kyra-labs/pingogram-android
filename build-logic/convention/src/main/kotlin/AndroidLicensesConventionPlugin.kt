import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import dev.kyralabs.pingogram.buildlogic.OpenSourceLicenses
import dev.kyralabs.pingogram.buildlogic.OpenSourceLicensesTask
import dev.kyralabs.pingogram.buildlogic.PomResolver
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * `:app` only (only the app knows every dependency it ships): the open-source licenses list of each variant
 * (`docs/adr/0002-open-source-licenses.md`). `generate<Variant>OpenSourceLicenses` ([OpenSourceLicensesTask]) lists
 * the variant's runtime dependencies with their POM licenses, completed by `config/licenses/`, into the generated
 * resource `raw/open_source_licenses.json` of the variant, every flavor and build type (`benchmarkRelease` and
 * `nonMinifiedRelease` included). `:app` reads it as `R.raw.open_source_licenses`, which keeps it through R8's
 * resource shrinking.
 */
class AndroidLicensesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val texts = layout.settingsDirectory.dir(OpenSourceLicenses.TEXTS_DIRECTORY)
            val extras = layout.settingsDirectory.file(OpenSourceLicenses.EXTRA_FILE)
            val resolver = PomResolver(configurations, dependencies, logger)

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationAndroidComponentsExtension> {
                    onVariants { variant ->
                        val name = variant.name.replaceFirstChar(Char::uppercaseChar)
                        val task =
                            tasks.register<OpenSourceLicensesTask>("generate${name}OpenSourceLicenses") {
                                group = "build"
                                description =
                                    "Lists the open-source libraries of ${variant.name} and their licenses into a " +
                                    "raw resource."
                                variantName.set(variant.name)
                                // Mapped while the configuration cache entry is stored (PomResolver explains why).
                                libraries.set(
                                    variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                                        .map(resolver::resolve),
                                )
                                licenseTexts.set(texts)
                                extraLibraries.set(extras)
                            }
                        variant.sources.res?.addGeneratedSourceDirectory(task, OpenSourceLicensesTask::outputDirectory)
                    }
                }
            }
        }
    }
}
