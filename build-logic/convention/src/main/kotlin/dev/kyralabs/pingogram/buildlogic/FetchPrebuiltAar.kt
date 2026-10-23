package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.register

/** What [PrebuiltAar.ensure] needs; shared by [PrebuiltAarSource] and [FetchPrebuiltAar]. */
interface PrebuiltAarSpec {
    /** [PrebuiltAar.name] of the AAR. */
    val aarName: Property<String>

    /** The file-based Maven repository ([PrebuiltAar.repository]). */
    val repository: DirectoryProperty

    val commit: Property<String>
    val pinnedSha256: Property<String>

    /** A self-built AAR from [PrebuiltAar.overrideProperty], if set. */
    val overrideAar: RegularFileProperty

    val offline: Property<Boolean>
}

private fun PrebuiltAarSpec.ensure(log: (String) -> Unit): String =
    PrebuiltAar.named(aarName.get()).ensure(
        repository = repository.get().asFile,
        commit = commit.get(),
        pinnedSha256 = pinnedSha256.get(),
        overrideAar = overrideAar.orNull?.asFile,
        offline = offline.get(),
        log = log,
    )

/**
 * Installs a prebuilt AAR when Gradle resolves the dependencies of the module that uses it and returns its Maven
 * version.
 *
 * A [ValueSource] because, with the configuration cache, dependencies are resolved while the cache entry is stored,
 * so the AAR must exist at configuration time; and Gradle obtains the value again before reusing an entry, which
 * re-installs a deleted or modified AAR even when nothing else changed.
 */
abstract class PrebuiltAarSource : ValueSource<String, PrebuiltAarSource.Parameters> {
    interface Parameters :
        ValueSourceParameters,
        PrebuiltAarSpec

    override fun obtain(): String = parameters.ensure(Logging.getLogger(PrebuiltAarSource::class.java)::lifecycle)
}

/**
 * `./gradlew fetchTdlib`, `fetchNtgcalls`: downloads (or copies the override of) the AAR now, e.g. to prepare an
 * offline build. Regular builds do not need it; they install the AAR through [PrebuiltAarSource].
 */
@UntrackedTask(because = "Checks the installed AAR every time; the user or another build may have changed it")
abstract class FetchPrebuiltAar :
    DefaultTask(),
    PrebuiltAarSpec {
    @get:Input
    abstract override val aarName: Property<String>

    @get:Internal
    abstract override val repository: DirectoryProperty

    @get:Input
    abstract override val commit: Property<String>

    @get:Input
    abstract override val pinnedSha256: Property<String>

    @get:Internal
    abstract override val overrideAar: RegularFileProperty

    @get:Internal
    abstract override val offline: Property<Boolean>

    @TaskAction
    fun fetch() {
        val aar = PrebuiltAar.named(aarName.get())
        val version = ensure(logger::lifecycle)
        logger.lifecycle(
            "${aar.label} AAR ${aar.group}:${aar.name}:$version is installed in ${repository.get().asFile}",
        )
    }
}

/**
 * Puts [aar] on [configuration] of the Android library this is applied to, and registers [taskName] to fetch it on
 * demand.
 */
internal fun Project.usePrebuiltAar(
    aar: PrebuiltAar,
    configuration: String,
    taskName: String,
) {
    // Taken out of the lambdas below: one that reached `layout` would capture the Project, which the configuration
    // cache cannot store with the task.
    val root = layout.settingsDirectory.asFile
    val configure: PrebuiltAarSpec.() -> Unit = {
        aarName.set(aar.name)
        repository.set(layout.settingsDirectory.dir(aar.repository))
        commit.set(libs.findVersion(aar.commitCatalogKey).get().requiredVersion)
        pinnedSha256.set(libs.findVersion(aar.sha256CatalogKey).get().requiredVersion)
        // Relative to the repository root; File.resolve keeps an absolute path as it is.
        overrideAar.fileProvider(
            providers
                .gradleProperty(aar.overrideProperty)
                .map { root.resolve(it.trim()) },
        )
        offline.set(gradle.startParameter.isOffline)
    }

    // Obtained while the module is configured (AGP looks at every declared dependency in afterEvaluate), and again
    // before each reuse of a configuration cache entry.
    val version = providers.of(PrebuiltAarSource::class) { parameters(configure) }
    pluginManager.withPlugin("com.android.library") {
        dependencies.addProvider(configuration, version.map { "${aar.group}:${aar.name}:$it" })
    }

    tasks.register<FetchPrebuiltAar>(taskName) {
        group = "build setup"
        description =
            "Downloads the pinned ${aar.label} AAR (or copies ${aar.overrideProperty}) into ${aar.repository}."
        configure()
    }
}
