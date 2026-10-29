import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Kotlin warnings fail the module's compilations, its tests' included (`allWarningsAsErrors`). A module applies it
 * once its warnings are fixed, so a deprecation or an unused expression cannot come back unseen; the modules still
 * without it, and their warnings, are listed in `docs/audit/v1-readiness.md`. A warning that cannot be fixed yet is
 * suppressed where it is, with a comment saying why, never for the whole module.
 */
class KotlinWarningsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions.allWarningsAsErrors.set(true)
        }
    }
}
