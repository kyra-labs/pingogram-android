import dev.kyralabs.pingogram.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

/** Plain Kotlin module with no Android dependency (e.g. `:core:domain`). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "pingogram.android.lint")
            configureKotlinJvm()
        }
    }
}
