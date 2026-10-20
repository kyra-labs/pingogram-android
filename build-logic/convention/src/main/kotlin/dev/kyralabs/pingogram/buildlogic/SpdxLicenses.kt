package dev.kyralabs.pingogram.buildlogic

/**
 * A license the open-source licenses list knows: its SPDX identifier ([id], https://spdx.org/licenses/), the name and
 * link the app shows, and the [names] and [urls] POMs use for it. Its full text is
 * `config/licenses/texts/<id>.txt` ([OpenSourceLicenses]).
 */
data class SpdxLicense(
    val id: String,
    val name: String,
    val url: String,
    val names: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
)

/**
 * The table that maps the free-form `<license>` of a POM to an SPDX id ([match]). It holds the licenses found in the
 * app's dependency graph and in `config/licenses/extra.json`, each with the spellings and links its POMs use beside
 * its SPDX id, name and link, which always match. Names and links are compared loosely ([normalizedName],
 * [normalizedUrl]), so `http://www.apache.org/licenses/LICENSE-2.0.txt` matches
 * `https://apache.org/licenses/LICENSE-2.0.txt`, and "The Apache License, Version 2.0" matches "Apache License,
 * Version 2.0".
 *
 * A dependency under a license that is not here fails the build ([OpenSourceLicenses.generate]): add the spelling to
 * its entry, or a new entry with the license's text in `config/licenses/texts/`, after checking that the app may ship
 * under it.
 */
object SpdxLicenses {
    val all: List<SpdxLicense> =
        listOf(
            SpdxLicense(
                id = "Apache-2.0",
                name = "Apache License 2.0",
                url = "https://www.apache.org/licenses/LICENSE-2.0",
                names =
                    listOf(
                        "Apache 2.0",
                        "Apache License, Version 2.0",
                        "The Apache License, Version 2.0",
                        "The Apache Software License, Version 2.0",
                    ),
                urls = listOf("https://www.apache.org/licenses/LICENSE-2.0.txt"),
            ),
            // Protocol Buffers (androidx.datastore:datastore-preferences-external-protobuf).
            SpdxLicense(
                id = "BSD-3-Clause",
                name = "BSD 3-Clause License",
                url = "https://opensource.org/licenses/BSD-3-Clause",
            ),
            // TDLib (config/licenses/extra.json).
            SpdxLicense(
                id = "BSL-1.0",
                name = "Boost Software License 1.0",
                url = "https://www.boost.org/LICENSE_1_0.txt",
            ),
            // Google Sans Flex (config/licenses/extra.json).
            SpdxLicense(
                id = "OFL-1.1",
                name = "SIL Open Font License 1.1",
                url = "https://openfontlicense.org",
            ),
            // Unicode's emoji data, behind the emoji panel (config/licenses/extra.json).
            SpdxLicense(
                id = "Unicode-3.0",
                name = "Unicode License v3",
                url = "https://www.unicode.org/license.txt",
            ),
            // Google Play services and a few Firebase libraries, gms flavor only (docs/adr/0004-push-delivery.md): not
            // open source and not on the SPDX list, hence SPDX's LicenseRef- form. The text is the one the SDK manager
            // ships with every SDK package (package.xml, license "android-sdk-license"); the link shows Google's
            // current version.
            SpdxLicense(
                id = "LicenseRef-Android-SDK",
                name = "Android Software Development Kit License",
                url = "https://developer.android.com/studio/terms",
            ),
        )

    private val byId: Map<String, SpdxLicense> = all.associateBy(SpdxLicense::id)
    private val byName: Map<String, SpdxLicense> = index { license -> license.names.map(::normalizedName) }
    private val byUrl: Map<String, SpdxLicense> = index { license -> license.urls.map(::normalizedUrl) }

    operator fun get(id: String): SpdxLicense? = byId[id]

    /** What [match] found for a `<license>`. */
    sealed interface Match {
        data class Found(
            val license: SpdxLicense,
        ) : Match

        /** Neither its name nor its link is in the table. */
        data object Unknown : Match

        /** Its name and its link name two different licenses: someone has to look. */
        data class Conflict(
            val byName: SpdxLicense,
            val byUrl: SpdxLicense,
        ) : Match
    }

    /** The license a POM's `<license>` names, by its name or its link. */
    fun match(license: PomLicense): Match {
        val named = license.name?.let { byName[normalizedName(it)] }
        val linked = license.url?.let { byUrl[normalizedUrl(it)] }
        return when {
            named != null && linked != null && named != linked -> Match.Conflict(named, linked)
            named != null -> Match.Found(named)
            linked != null -> Match.Found(linked)
            else -> Match.Unknown
        }
    }

    /** Lower case, words only: "The Apache Software License, Version 2.0" → "apache software license version 2 0". */
    internal fun normalizedName(name: String): String =
        name
            .lowercase()
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .removePrefix("the ")

    /**
     * Lower case, without scheme, `www.`, a trailing slash or a `.txt`, `.html` or `.php` extension:
     * `https://www.apache.org/licenses/LICENSE-2.0.txt` → `apache.org/licenses/license-2.0`.
     */
    internal fun normalizedUrl(url: String): String =
        url
            .trim()
            .lowercase()
            .replace(SCHEME, "")
            .removePrefix("www.")
            .trimEnd('/')
            .replace(EXTENSION, "")

    private fun index(keys: (SpdxLicense) -> List<String>): Map<String, SpdxLicense> =
        buildMap {
            all.forEach { license ->
                (
                    keys(license) +
                        keys(license.copy(names = listOf(license.id, license.name), urls = listOf(license.url)))
                ).forEach { key ->
                    val previous = put(key, license)
                    check(previous == null || previous == license) { "'$key' is in two SPDX entries" }
                }
            }
        }
}

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val SCHEME = Regex("^https?://")
private val EXTENSION = Regex("""\.(txt|html?|php)$""")
