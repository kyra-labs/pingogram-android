package dev.kyralabs.pingogram.licenses

import android.content.Context
import android.content.res.Resources
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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OpenSourceLicensesRepository] on the list the build generates (`docs/adr/0002-open-source-licenses.md`):
 * build-logic's `pingogram.android.licenses` writes each variant's runtime dependencies, with their POM licenses, the
 * entries of `config/licenses/extra.json` and every license text, into the raw resource `open_source_licenses`, read
 * here with the platform's `org.json`. It is read and mapped once, on the IO dispatcher, the first time a screen asks,
 * then kept for the process (about a hundred KB).
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
                        context.resources
                            .openRawResource(R.raw.open_source_licenses)
                            .use { it.readBytes().decodeToString() },
                    )
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: Resources.NotFoundException) {
                    Result.failure(e)
                }
            return read.fold(
                onSuccess = { json ->
                    val libraries =
                        try {
                            openSourceLibraries(json)
                        } catch (e: JSONException) {
                            return unreadable(e)
                        }
                    // The build always lists TDLib at least: nothing means the resource could not be parsed.
                    if (libraries.isEmpty()) unreadable(null) else Outcome.Success(libraries)
                },
                onFailure = { unreadable(it) },
            )
        }

        private fun unreadable(cause: Throwable?): Outcome.Failure =
            Outcome.Failure(
                DomainError.Unknown(code = 0, message = "open_source_licenses unreadable: ${cause?.javaClass?.name}"),
            )
    }

/**
 * The libraries in the generated [json], as the screens show them: sorted by name (case-insensitively, then by id, so
 * two libraries of one name keep their order), with their licenses sorted by name. The format is build-logic's
 * (`OpenSourceLicenses`): `{"formatVersion": 1, "libraries": [...], "licenses": {spdxId: {name, url, text}}}`; each
 * library names its licenses by SPDX id. Throws [JSONException] if the file cannot be parsed.
 */
internal fun openSourceLibraries(json: String): ImmutableList<OpenSourceLibrary> {
    val root = JSONObject(json)
    val licenses = root.optJSONObject("licenses") ?: JSONObject()
    return root
        .optJSONArray("libraries")
        .objects()
        .map { it.toOpenSourceLibrary(licenses) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLibrary::name).thenBy(OpenSourceLibrary::id))
        .toImmutableList()
}

private fun JSONObject.toOpenSourceLibrary(licenses: JSONObject): OpenSourceLibrary {
    val id = getString("id")
    return OpenSourceLibrary(
        id = id,
        name = text("name") ?: id,
        version = text("version")?.let(::displayVersion),
        description = text("description"),
        developers = optJSONArray("developers").strings().toImmutableList(),
        website = text("website"),
        licenses =
            optJSONArray("licenses")
                .strings()
                .map { licenseId -> licenses.optJSONObject(licenseId).toOpenSourceLicense(licenseId) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLicense::name))
                .toImmutableList(),
    )
}

/** The license [id] refers to, from its entry in the file; just its id if the file has no entry for it. */
private fun JSONObject?.toOpenSourceLicense(id: String): OpenSourceLicense =
    OpenSourceLicense(
        id = id,
        name = this?.text("name") ?: id,
        url = this?.text("url"),
        text = this?.text("text"),
    )

/** The string at [key], or null if it is missing, null or blank. */
private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }

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
