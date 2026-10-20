package dev.kyralabs.pingogram.buildlogic

import groovy.json.JsonGenerator
import groovy.json.JsonSlurper
import java.io.Serializable

/**
 * A Maven component of an app variant's runtime classpath, as [resolveMavenLibraries] found it: its POM with its
 * parents' values ([Pom.effective]), or null if no POM could be resolved. [problem] says what could not be resolved
 * (the POM, or a parent POM), for the failure message if the library ends up without a license.
 */
data class MavenLibrary(
    val coordinates: MavenCoordinates,
    val pom: EffectivePom?,
    val problem: String? = null,
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * An entry of `config/licenses/extra.json`: what Maven does not say. An [id] with a colon (`group:artifact`) completes
 * that dependency (TDLib: its POM, written by the build, has no license); its values replace the POM's, its version
 * stays the dependency's. An id without one (`google-sans-flex`) is a library of its own, listed in every variant.
 * [licenses] are SPDX ids of [SpdxLicenses].
 */
data class ExtraLibrary(
    val id: String,
    val name: String?,
    val version: String?,
    val description: String?,
    val website: String?,
    val developers: List<String>,
    val licenses: List<String>,
) {
    val completesDependency: Boolean get() = ':' in id
}

/**
 * The open-source licenses list of an app variant (`docs/adr/0002-open-source-licenses.md`): its Maven dependencies
 * with the licenses of their POMs mapped to SPDX ids ([SpdxLicenses]), completed by `config/licenses/extra.json`,
 * with the full text of every license from `config/licenses/texts/<SPDX id>.txt`. [OpenSourceLicensesTask] writes it
 * as `res/raw/open_source_licenses.json`, which `:app`'s `GeneratedOpenSourceLicensesRepository` reads:
 *
 * ```
 * {
 *   "formatVersion": 1,
 *   "libraries": [
 *     { "id": "androidx.activity:activity", "name": "Activity", "version": "1.13.0",
 *       "description": "…", "website": "https://…", "developers": ["The Android Open Source Project"],
 *       "licenses": ["Apache-2.0"] }
 *   ],
 *   "licenses": {
 *     "Apache-2.0": { "name": "Apache License 2.0", "url": "https://…", "text": "…" }
 *   }
 * }
 * ```
 *
 * Libraries are sorted by id; `version`, `description` and `website` are left out where unknown. Each license text is
 * written once, however many libraries are under it.
 *
 * It is a compliance gate: a dependency without a license that maps to an SPDX id, or a license without a text, fails
 * the build with a message that names the artifact and what to add.
 */
object OpenSourceLicenses {
    /** Relative to the repository root. */
    const val TEXTS_DIRECTORY = "config/licenses/texts"
    const val EXTRA_FILE = "config/licenses/extra.json"

    /** `res/raw/open_source_licenses.json`, `R.raw.open_source_licenses` in `:app`. */
    const val RESOURCE_NAME = "open_source_licenses"
    const val FORMAT_VERSION = 1

    data class Library(
        val id: String,
        val name: String,
        val version: String?,
        val description: String?,
        val website: String?,
        val developers: List<String>,
        val licenses: List<String>,
    )

    data class License(
        val name: String,
        val url: String,
        val text: String,
    )

    data class Generated(
        val libraries: List<Library>,
        val licenses: Map<String, License>,
    )

    /**
     * The list of [variant] from its Maven [dependencies], the [extras] of `config/licenses/extra.json` and the license
     * [texts] (by SPDX id). Throws [IllegalStateException] listing every library it cannot license.
     */
    fun generate(
        variant: String,
        dependencies: List<MavenLibrary>,
        extras: List<ExtraLibrary>,
        texts: Map<String, String>,
    ): Generated {
        val problems = mutableListOf<String>()
        val extrasById = extras.associateBy(ExtraLibrary::id)
        val dependencyIds = dependencies.mapTo(mutableSetOf()) { it.coordinates.id }
        extras.filter { it.completesDependency && it.id !in dependencyIds }.forEach {
            problems +=
                "$EXTRA_FILE completes ${it.id}, which is not a dependency of $variant: fix or remove the entry."
        }

        val libraries =
            dependencies.mapNotNull { dependency ->
                val extra = extrasById[dependency.coordinates.id]
                if (extra != null) {
                    merged(dependency, extra)
                } else {
                    mavenLibrary(dependency, problems)
                }
            } + extras.filterNot(ExtraLibrary::completesDependency).map(::standalone)

        val licenses =
            libraries
                .flatMap { library -> library.licenses.map { it to library.id } }
                .groupBy({ it.first }, { it.second })
                .toSortedMap()
                .mapNotNull { (id, users) ->
                    val license = SpdxLicenses[id]
                    val text = texts[id]?.takeIf { it.isNotBlank() }
                    when {
                        license == null -> {
                            problems += "${users.joinToString()}: $EXTRA_FILE names the license $id, which is not " +
                                "in the SPDX table (SpdxLicenses in build-logic)."
                            null
                        }

                        text == null -> {
                            problems += "${users.joinToString()}: no text for the license $id. Add it as " +
                                "$TEXTS_DIRECTORY/$id.txt."
                            null
                        }

                        else -> {
                            id to License(license.name, license.url, text)
                        }
                    }
                }.toMap()

        check(problems.isEmpty()) {
            "The open-source licenses of $variant cannot be listed (docs/adr/0002-open-source-licenses.md):\n" +
                problems.sorted().joinToString("\n") { "- $it" }
        }
        return Generated(libraries.sortedBy(Library::id), licenses)
    }

    private fun mavenLibrary(
        dependency: MavenLibrary,
        problems: MutableList<String>,
    ): Library? {
        val coordinates = dependency.coordinates
        val pom = dependency.pom
        val declared = pom?.licenses.orEmpty()
        if (declared.isEmpty()) {
            val why = dependency.problem ?: "its POM and parent POMs declare no license"
            problems += "$coordinates: $why. If it has a license, add an entry for ${coordinates.id} to $EXTRA_FILE."
            return null
        }
        val licenses =
            declared.mapNotNull { license ->
                when (val match = SpdxLicenses.match(license)) {
                    is SpdxLicenses.Match.Found -> {
                        match.license.id
                    }

                    is SpdxLicenses.Match.Conflict -> {
                        problems += "$coordinates: the POM license $license names ${match.byName.id} but links " +
                            "to ${match.byUrl.id}. Check which applies and add an entry for ${coordinates.id} to " +
                            "$EXTRA_FILE."
                        null
                    }

                    SpdxLicenses.Match.Unknown -> {
                        problems += "$coordinates: the POM license $license is not in the SPDX table. If it is one " +
                            "of its licenses, add the spelling to SpdxLicenses (build-logic); otherwise check that " +
                            "the app may ship under it and add an entry with its text in $TEXTS_DIRECTORY."
                        null
                    }
                }
            }
        if (licenses.size < declared.size) return null
        return Library(
            id = coordinates.id,
            name = pom?.name ?: coordinates.artifact,
            version = coordinates.version,
            description = pom?.description,
            website = pom?.website,
            developers = pom?.developers.orEmpty(),
            licenses = licenses.distinct(),
        )
    }

    private fun merged(
        dependency: MavenLibrary,
        extra: ExtraLibrary,
    ): Library {
        val pom = dependency.pom
        return Library(
            id = extra.id,
            name = extra.name ?: pom?.name ?: dependency.coordinates.artifact,
            version = dependency.coordinates.version,
            description = extra.description ?: pom?.description,
            website = extra.website ?: pom?.website,
            developers = extra.developers.ifEmpty { pom?.developers.orEmpty() },
            licenses = extra.licenses.distinct(),
        )
    }

    private fun standalone(extra: ExtraLibrary): Library =
        Library(
            id = extra.id,
            name = extra.name ?: extra.id,
            version = extra.version,
            description = extra.description,
            website = extra.website,
            developers = extra.developers,
            licenses = extra.licenses.distinct(),
        )

    /** The entries of `config/licenses/extra.json` ([ExtraLibrary]); throws [IllegalArgumentException] if malformed. */
    fun parseExtras(json: String): List<ExtraLibrary> {
        val root = JsonSlurper().parseText(json) as? Map<*, *>
        val entries = root?.get("libraries") as? List<*>
        requireNotNull(entries) { "$EXTRA_FILE must be an object with a \"libraries\" array." }
        return entries.map { entry ->
            require(entry is Map<*, *>) { "Every entry of \"libraries\" in $EXTRA_FILE must be an object." }
            val id = entry.string("id")
            requireNotNull(id) { "An entry of $EXTRA_FILE has no \"id\"." }
            val licenses = entry.strings("licenses")
            require(licenses.isNotEmpty()) { "The entry $id of $EXTRA_FILE has no \"licenses\" (SPDX ids)." }
            ExtraLibrary(
                id = id,
                name = entry.string("name"),
                version = entry.string("version"),
                description = entry.string("description"),
                website = entry.string("website"),
                developers = entry.strings("developers"),
                licenses = licenses,
            )
        }
    }

    /** [generated] in the format described on [OpenSourceLicenses], compact, keys in a fixed order. */
    fun toJson(generated: Generated): String {
        val document =
            linkedMapOf(
                "formatVersion" to FORMAT_VERSION,
                "libraries" to
                    generated.libraries.map { library ->
                        linkedMapOf(
                            "id" to library.id,
                            "name" to library.name,
                            "version" to library.version,
                            "description" to library.description,
                            "website" to library.website,
                            "developers" to library.developers,
                            "licenses" to library.licenses,
                        )
                    },
                "licenses" to
                    generated.licenses.mapValues { (_, license) ->
                        linkedMapOf("name" to license.name, "url" to license.url, "text" to license.text)
                    },
            )
        return JSON.toJson(document)
    }

    private val JSON: JsonGenerator =
        JsonGenerator
            .Options()
            .excludeNulls()
            .disableUnicodeEscaping()
            .build()

    private fun Map<*, *>.string(key: String): String? = (get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun Map<*, *>.strings(key: String): List<String> =
        (get(key) as? List<*>).orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) }
}
