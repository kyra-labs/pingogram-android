import com.android.build.api.dsl.CommonExtension
import dev.kyralabs.pingogram.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Apply after `pingogram.android.application` or `pingogram.android.library`.
 *
 * The Compose compiler reads the stability configuration in `config/compose/compose_compiler_config.conf`
 * (`docs/PERFORMANCE.md` §4). `-Ppingogram.composeCompilerReports` writes its reports and metrics (which classes are
 * stable, which composables skip) to `build/compose-compiler/`, e.g.
 * `./gradlew :feature:chat:compileDebugKotlin -Ppingogram.composeCompilerReports`.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().buildFeatures.compose = true

            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    layout.settingsDirectory.file("config/compose/compose_compiler_config.conf"),
                )
                if (providers.gradleProperty(REPORTS_PROPERTY).isPresent) {
                    reportsDestination.set(layout.buildDirectory.dir("compose-compiler"))
                    metricsDestination.set(layout.buildDirectory.dir("compose-compiler"))
                }
            }

            dependencies {
                val bom = platform(libs.findLibrary("androidx-compose-bom").get())
                "implementation"(bom)
                "androidTestImplementation"(bom)
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }

    private companion object {
        const val REPORTS_PROPERTY = "pingogram.composeCompilerReports"
    }
}
