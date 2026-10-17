import dev.kyralabs.pingogram.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/**
 * A `:feature:*` module: Compose UI + Hilt ViewModels on top of the core modules (`docs/ARCHITECTURE.md`),
 * with Roborazzi screenshot tests (`docs/TESTING.md`). Each feature adds its destinations to the app's
 * `NavHost` through `NavGraphBuilder` extensions, using the routes in `:core:navigation`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "pingogram.android.library")
            apply(plugin = "pingogram.android.compose")
            apply(plugin = "pingogram.android.hilt")
            apply(plugin = "pingogram.android.roborazzi")

            dependencies {
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:navigation"))
                "implementation"(project(":core:ui"))
                "implementation"(libs.findLibrary("androidx-navigation-compose").get())
            }
        }
    }
}
