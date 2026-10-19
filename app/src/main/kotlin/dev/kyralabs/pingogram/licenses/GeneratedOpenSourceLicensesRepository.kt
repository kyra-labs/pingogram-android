package dev.kyralabs.pingogram.licenses

import android.content.Context
import android.content.res.Resources
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLicense
import dev.kyralabs.pingogram.core.domain.repository.OpenSourceLicensesRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OpenSourceLicensesRepository] on the list the build generates (`docs/adr/0002-open-source-licenses.md`): the
 * AboutLibraries Gradle plugin writes each variant's runtime dependencies, with the entries and license texts of
 * `config/aboutlibraries`, into the raw resource `aboutlibraries`, which `aboutlibraries-core` reads. It is read and
 * mapped once, on the IO dispatcher, the first time a screen asks, then kept for the process (a few hundred KB).
 */
@Singleton
class GeneratedOpenSourceLicensesRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : OpenSourceLicensesRepository {
        private val mutex = Mutex()
        private var libraries: ImmutableList<OpenSourceLibrary>? = null

        override suspend fun libraries(): Outcome<ImmutableList<OpenSourceLibrary>> =
            mutex.withLock {
                libraries?.let { return@withLock Outcome.Success(it) }
                val loaded = withContext(io) { load() }
                if (loaded is Outcome.Success) libraries = loaded.value
                loaded
            }

        override suspend fun library(id: String): Outcome<OpenSourceLibrary> =
            when (val all = libraries()) {
                is Outcome.Success -> {
                    all.value.firstOrNull { it.id == id }?.let { Outcome.Success(it) }
                        ?: Outcome.Failure(DomainError.NotFound)
                }

                is Outcome.Failure -> {
                    all
                }
            }

        private fun load(): Outcome<ImmutableList<OpenSourceLibrary>> {
            val read =
                try {
                    Result.success(
                        context.resources.openRawResource(R.raw.aboutlibraries).use { it.readBytes().decodeToString() },
                    )
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: Resources.NotFoundException) {
                    Result.failure(e)
                }
            return read.fold(
                onSuccess = { json ->
                    val libraries = openSourceLibraries(json)
                    // The build always lists TDLib at least: nothing means the resource could not be parsed.
                    if (libraries.isEmpty()) unreadable(null) else Outcome.Success(libraries)
                },
                onFailure = { unreadable(it) },
            )
        }

        private fun unreadable(cause: Throwable?): Outcome.Failure =
            Outcome.Failure(
                DomainError.Unknown(code = 0, message = "aboutlibraries unreadable: ${cause?.javaClass?.name}"),
            )
    }

/**
 * The libraries in AboutLibraries' [json], as the screens show them: sorted by name (case-insensitively, then by id,
 * so two libraries of one name keep their order), with their licenses sorted by name.
 */
internal fun openSourceLibraries(json: String): ImmutableList<OpenSourceLibrary> =
    Libs
        .Builder()
        .withJson(json)
        .build()
        .libraries
        .map { it.toOpenSourceLibrary() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLibrary::name).thenBy(OpenSourceLibrary::id))
        .toImmutableList()

private fun Library.toOpenSourceLibrary(): OpenSourceLibrary =
    OpenSourceLibrary(
        id = uniqueId,
        name = name.ifBlank { uniqueId },
        version = artifactVersion?.takeIf { it.isNotBlank() }?.let(::displayVersion),
        description = description?.takeIf { it.isNotBlank() },
        developers =
            developers
                .mapNotNull { developer -> developer.name?.takeIf { it.isNotBlank() } }
                .ifEmpty { listOfNotNull(organization?.name?.takeIf { it.isNotBlank() }) }
                .toImmutableList(),
        website = website?.takeIf { it.isNotBlank() } ?: scm?.url?.takeIf { it.isNotBlank() },
        licenses =
            licenses
                .map { it.toOpenSourceLicense() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLicense::name))
                .toImmutableList(),
    )

private fun License.toOpenSourceLicense(): OpenSourceLicense =
    OpenSourceLicense(
        id = spdxId?.takeIf { it.isNotBlank() } ?: hash,
        name = name.ifBlank { spdxId ?: hash },
        url = url?.takeIf { it.isNotBlank() },
        text = licenseContent?.takeIf { it.isNotBlank() },
    )

/**
 * [version] as people read it: a git commit (TDLib's version is the commit it is built from, `docs/TDLIB.md`) by its
 * first [SHORT_COMMIT_LENGTH] characters, as its releases are named (`tdlib-<short-sha>`); anything else as it is.
 */
internal fun displayVersion(version: String): String {
    val isCommit =
        version.length >= COMMIT_LENGTH && version.take(COMMIT_LENGTH).all { it in '0'..'9' || it in 'a'..'f' }
    return if (isCommit) version.take(SHORT_COMMIT_LENGTH) + version.drop(COMMIT_LENGTH) else version
}

/** A git commit id: 40 hexadecimal digits. A self-built TDLib's version adds `-local` (`docs/TDLIB.md`). */
private const val COMMIT_LENGTH = 40
private const val SHORT_COMMIT_LENGTH = 10
