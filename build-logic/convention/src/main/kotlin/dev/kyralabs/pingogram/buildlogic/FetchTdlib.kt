package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
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

/** What [TdlibAar.ensure] needs; shared by [TdlibAarSource] and [FetchTdlib]. */
interface TdlibAarSpec {
    /** The file-based Maven repository ([TdlibAar.REPOSITORY]). */
    val repository: DirectoryProperty

    val commit: Property<String>
    val pinnedSha256: Property<String>

    /** A self-built AAR from [TdlibAar.OVERRIDE_PROPERTY], if set. */
    val overrideAar: RegularFileProperty

    val offline: Property<Boolean>
}

private fun TdlibAarSpec.ensure(log: (String) -> Unit): String =
    TdlibAar.ensure(
        repository = repository.get().asFile,
        commit = commit.get(),
        pinnedSha256 = pinnedSha256.get(),
        overrideAar = overrideAar.orNull?.asFile,
        offline = offline.get(),
        log = log,
    )

/**
 * Installs the TDLib AAR when Gradle resolves `:core:tdlib`'s dependencies and returns its Maven version.
 *
 * A [ValueSource] because, with the configuration cache, dependencies are resolved while the cache entry is stored,
 * so the AAR must exist at configuration time; and Gradle obtains the value again before reusing an entry, which
 * re-installs a deleted or modified AAR even when nothing else changed.
 */
abstract class TdlibAarSource : ValueSource<String, TdlibAarSource.Parameters> {
    interface Parameters :
        ValueSourceParameters,
        TdlibAarSpec

    override fun obtain(): String = parameters.ensure(Logging.getLogger(TdlibAarSource::class.java)::lifecycle)
}

/**
 * `./gradlew fetchTdlib`: downloads (or copies the override of) the TDLib AAR now, e.g. to prepare an offline build.
 * Regular builds do not need it; they install the AAR through [TdlibAarSource].
 */
@UntrackedTask(because = "Checks the installed AAR every time; the user or another build may have changed it")
abstract class FetchTdlib :
    DefaultTask(),
    TdlibAarSpec {
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
        val version = ensure(logger::lifecycle)
        logger.lifecycle(
            "TDLib AAR ${TdlibAar.GROUP}:${TdlibAar.NAME}:$version is installed in ${repository.get().asFile}",
        )
    }
}
