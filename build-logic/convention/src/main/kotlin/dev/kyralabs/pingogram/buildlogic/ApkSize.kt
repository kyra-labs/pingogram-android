package dev.kyralabs.pingogram.buildlogic

import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Locale

/**
 * The APK size budget of `docs/PERFORMANCE.md` §1: at most [ARM64_BUDGET_BYTES] for the arm64-v8a APK, which most
 * phones install, and [UNIVERSAL_BUDGET_BYTES] for the universal one (raised for the call engine by the owner,
 * 2026-10-22; set from the APKs with voice chats at their size plus a small margin, owner, 2026-10-23). A megabyte is
 * 1,048,576 bytes, as GitHub's release pages count it.
 */
private const val BYTES_PER_MEGABYTE = 1_048_576L

object ApkSize {
    const val ARM64_BUDGET_BYTES = 50L * BYTES_PER_MEGABYTE
    const val UNIVERSAL_BUDGET_BYTES = 123L * BYTES_PER_MEGABYTE

    /** The budget of the APK for [abi] (null: universal), or null where `docs/PERFORMANCE.md` sets none. */
    fun budgetOf(abi: String?): Long? =
        when (abi) {
            "arm64-v8a" -> ARM64_BUDGET_BYTES
            null -> UNIVERSAL_BUDGET_BYTES
            else -> null
        }

    /** One line per APK, sorted by ABI ("universal" for none): its size, and its budget where it has one. */
    fun report(sizes: Map<String?, Long>): String =
        sizes.entries
            .sortedBy { it.key ?: UNIVERSAL }
            .joinToString(separator = "\n", postfix = "\n") { (abi, bytes) ->
                val budget = budgetOf(abi)?.let { " (budget ${megabytes(it)})" }.orEmpty()
                "${abi ?: UNIVERSAL}: ${megabytes(bytes)}$budget"
            }

    /** The APKs of [sizes] over their budget, as sentences; empty when every one is within it. */
    fun violations(sizes: Map<String?, Long>): List<String> =
        sizes.mapNotNull { (abi, bytes) ->
            val budget = budgetOf(abi) ?: return@mapNotNull null
            "The ${abi ?: UNIVERSAL} APK is ${megabytes(bytes)}, over its budget of ${megabytes(budget)}"
                .takeIf { bytes > budget }
        }

    fun megabytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / BYTES_PER_MEGABYTE)

    private const val UNIVERSAL = "universal"
}

/**
 * Fails when an APK of the variant is over its budget ([ApkSize]) and writes every APK's size to [report];
 * `pingogram.android.application` registers it as `check<Variant>ApkSize` for the minified, non-debuggable
 * variants, and CI runs it on `fossBenchmarkRelease` (R8 as release, signed with the debug key).
 */
@DisableCachingByDefault(because = "Reads the size of files it does not produce; there is nothing to cache.")
abstract class ApkSizeCheckTask : DefaultTask() {
    @get:Input
    abstract val variantName: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val artifacts =
            builtArtifactsLoader.get().load(apkDirectory.get())
                ?: throw GradleException("No APKs of ${variantName.get()} in ${apkDirectory.get()}")
        val sizes =
            artifacts.elements.associate { element ->
                val abi = element.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
                abi to File(element.outputFile).length()
            }
        val text = ApkSize.report(sizes)
        report.get().asFile.writeText(text)
        logger.lifecycle("APK sizes of ${variantName.get()}:\n$text")
        val violations = ApkSize.violations(sizes)
        if (violations.isNotEmpty()) {
            throw GradleException(
                violations.joinToString(
                    separator = "\n",
                    postfix = "\nSee docs/PERFORMANCE.md §8 for what takes the space and how to shrink it.",
                ),
            )
        }
    }
}
