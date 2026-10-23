package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a `foss` variant's runtime classpath holds one of Google's services ([GoogleFree]); registered by
 * `pingogram.android.gms` for every `foss` variant and run by `check` and before the variant builds.
 */
@CacheableTask
abstract class GoogleFreeCheckTask : DefaultTask() {
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
        val violations = GoogleFree.violations(modules.get())
        if (violations.isNotEmpty()) throw GradleException(GoogleFree.message(variantName.get(), violations))
        report.get().asFile.writeText(modules.get().joinToString(separator = "\n", postfix = "\n"))
    }
}

/**
 * The external modules of the graph under [root], BOMs included (a platform of Google's services is as wrong as the
 * services themselves), sorted.
 */
internal fun moduleIds(root: ResolvedComponentResult): List<String> {
    val seen = mutableSetOf(root.id)
    val queue = ArrayDeque(listOf(root))
    val modules = sortedSetOf<String>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        (component.id as? ModuleComponentIdentifier)?.let { modules += "${it.group}:${it.module}:${it.version}" }
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .map { it.selected }
            .filter { seen.add(it.id) }
            .forEach(queue::addLast)
    }
    return modules.toList()
}
