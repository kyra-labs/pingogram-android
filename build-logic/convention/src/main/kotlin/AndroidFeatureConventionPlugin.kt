import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** A `:feature:*` module: Compose UI + Hilt ViewModels on top of the core modules (`docs/ARCHITECTURE.md`). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "pingogram.android.library")
            apply(plugin = "pingogram.android.compose")
            apply(plugin = "pingogram.android.hilt")

            dependencies {
                "implementation"(project(":core:domain"))
                "implementation"(project(":core:navigation"))
                "implementation"(project(":core:ui"))
            }
        }
    }
}
