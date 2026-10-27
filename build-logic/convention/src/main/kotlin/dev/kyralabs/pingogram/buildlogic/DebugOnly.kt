package dev.kyralabs.pingogram.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Tools that belong in debug builds only (`docs/PERFORMANCE.md` §9): LeakCanary, which dumps the heap and shows the
 * leaks it finds in a launcher icon of its own. Every other build type (`release`, and the Baseline Profile plugin's
 * `benchmarkRelease` and `nonMinifiedRelease`, which the benchmarks measure) must be without them.
 */
object DebugOnly {
    /** Maven groups of the debug-only tools. */
    val GROUPS = listOf("com.squareup.leakcanary")

    /** The build type that may have them. */
    const val BUILD_TYPE = "debug"

    /** The [modules] (`group:artifact:version`) of the debug-only tools, in their order. */
    fun violations(modules: List<String>): List<String> = modules.filter { it.substringBefore(':') in GROUPS }

    fun message(
        variant: String,
        violations: List<String>,
    ): String =
        "$variant must not depend on debug-only tools (docs/PERFORMANCE.md §9), but its runtime classpath has " +
            "${violations.joinToString()}. Declare them with \"debugImplementation\", or find what brings them in " +
            "with `./gradlew :app:dependencies --configuration ${variant}RuntimeClasspath`."
}

/**
 * Fails when a variant of another build type than [DebugOnly.BUILD_TYPE] has a debug-only tool on its runtime
 * classpath; registered for those variants by `pingogram.android.application`, run by `check` and before the variant
 * builds.
 */
@CacheableTask
abstract class DebugOnlyCheckTask : DefaultTask() {
    @get:Input
    abstract val variantName: Property<String>

    /** Every module of the variant's runtime classpath, `group:artifact:version` ([moduleIds]). */
    @get:Input
    abstract val modules: ListProperty<String>

    /** Lists the modules checked, so the task has an output to be up to date with. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val violations = DebugOnly.violations(modules.get())
        if (violations.isNotEmpty()) throw GradleException(DebugOnly.message(variantName.get(), violations))
        report.get().asFile.writeText(modules.get().joinToString(separator = "\n", postfix = "\n"))
    }
}

/** `check<Variant>NoDebugTools` ([DebugOnlyCheckTask]) for every variant that is not a debug build. */
internal fun Project.registerDebugOnlyChecks() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            if (variant.buildType == DebugOnly.BUILD_TYPE) return@onVariants
            val name = variant.name.replaceFirstChar(Char::uppercaseChar)
            val task =
                tasks.register<DebugOnlyCheckTask>("check${name}NoDebugTools") {
                    group = "verification"
                    description = "Checks that ${variant.name} depends on no debug-only tool."
                    variantName.set(variant.name)
                    modules.set(
                        variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                            .map(::moduleIds),
                    )
                    report.set(layout.buildDirectory.file("reports/debug-only/${variant.name}.txt"))
                }
            tasks.named("check") { dependsOn(task) }
            tasks.matching { it.name == "pre${name}Build" }.configureEach { dependsOn(task) }
        }
    }
}
