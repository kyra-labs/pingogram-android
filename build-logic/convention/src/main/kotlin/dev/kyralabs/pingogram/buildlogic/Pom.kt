package dev.kyralabs.pingogram.buildlogic

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.Serializable
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** A Maven module version, `group:artifact:version`. */
data class MavenCoordinates(
    val group: String,
    val artifact: String,
    val version: String,
) : Serializable {
    /** `group:artifact`, the id a library has on the open-source licenses screen. */
    val id: String get() = "$group:$artifact"

    override fun toString(): String = "$group:$artifact:$version"

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** A `<license>` of a POM, as written: either part may be missing. */
data class PomLicense(
    val name: String?,
    val url: String?,
) : Serializable {
    override fun toString(): String = listOfNotNull(name?.let { "'$it'" }, url).joinToString(" ").ifEmpty { "(empty)" }

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * The parts of a POM the open-source licenses list needs, as the file writes them, before anything is inherited from
 * its parent ([Pom.effective] does that). Values are trimmed, with runs of white space collapsed to one space;
 * `${…}` placeholders are left for [Pom.effective] to fill in.
 */
data class Pom(
    val coordinates: MavenCoordinates,
    val parent: MavenCoordinates?,
    val name: String?,
    val description: String?,
    val url: String?,
    val scmUrl: String?,
    val organization: String?,
    val developers: List<String>,
    val licenses: List<PomLicense>,
    val properties: Map<String, String>,
) {
    /**
     * What a library's entry shows: this POM with what Maven inherits from its [parents] (nearest first) filled in.
     * Maven inherits licenses, developers, organization, url and scm; not name or artifactId. The description is
     * inherited too, but a parent's describes the parent ("Guava Maven Parent"), so it is left out. `${…}`
     * placeholders are filled in from the chain's properties and project values; a value that still has one is
     * dropped rather than shown half-filled. Only `http(s)` links are kept.
     */
    fun effective(parents: List<Pom>): EffectivePom {
        val chain = listOf(this) + parents
        val properties = mutableMapOf<String, String>()
        chain.asReversed().forEach { properties += it.properties }
        properties +=
            mapOf(
                "project.groupId" to coordinates.group,
                "project.artifactId" to coordinates.artifact,
                "project.version" to coordinates.version,
                "pom.groupId" to coordinates.group,
                "pom.artifactId" to coordinates.artifact,
                "pom.version" to coordinates.version,
                "version" to coordinates.version,
            )
        parent?.let { properties += mapOf("project.parent.version" to it.version, "parent.version" to it.version) }

        fun String.filled(): String? = interpolate(this, properties)

        fun <T> inherited(value: (Pom) -> T?): T? = chain.firstNotNullOfOrNull(value)

        fun link(value: (Pom) -> String?): String? =
            chain.firstNotNullOfOrNull { pom -> value(pom)?.filled()?.takeIf { it.isHttp() } }

        return EffectivePom(
            name = name?.filled(),
            description = description?.filled(),
            website = link(Pom::url) ?: link(Pom::scmUrl),
            developers =
                (inherited { pom -> pom.developers.ifEmpty { null } } ?: listOfNotNull(inherited(Pom::organization)))
                    .mapNotNull { it.filled() },
            licenses =
                inherited { pom -> pom.licenses.ifEmpty { null } }
                    .orEmpty()
                    .map { PomLicense(it.name?.filled(), it.url?.filled()) },
        )
    }

    companion object {
        /**
         * Reads the POM in [xml]; [source] names it in errors. Throws [IllegalArgumentException] if it is not a POM.
         * DTDs and external entities are never loaded (no file or network access from a POM).
         */
        fun parse(
            xml: String,
            source: String,
        ): Pom {
            val document =
                try {
                    documentBuilderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
                } catch (e: org.xml.sax.SAXException) {
                    throw IllegalArgumentException("$source is not a readable POM: ${e.message}", e)
                }
            val project = document.documentElement
            require(project.tagName == "project") { "$source is not a POM: its root element is <${project.tagName}>" }
            val parent =
                project.child("parent")?.let { element ->
                    val group = element.text("groupId")
                    val artifact = element.text("artifactId")
                    val version = element.text("version")
                    if (group != null && artifact != null && version != null) {
                        MavenCoordinates(group, artifact, version)
                    } else {
                        null
                    }
                }
            val group = project.text("groupId") ?: parent?.group
            val artifact = project.text("artifactId")
            val version = project.text("version") ?: parent?.version
            require(group != null && artifact != null && version != null) {
                "$source is not a POM: it has no groupId, artifactId or version"
            }
            return Pom(
                coordinates = MavenCoordinates(group, artifact, version),
                parent = parent,
                name = project.text("name"),
                description = project.text("description"),
                url = project.text("url"),
                scmUrl = project.child("scm")?.text("url"),
                organization = project.child("organization")?.text("name"),
                developers =
                    project.child("developers").children("developer").mapNotNull { developer ->
                        developer.text("name") ?: developer.text("organization") ?: developer.text("id")
                    },
                licenses =
                    project
                        .child("licenses")
                        .children("license")
                        .map { PomLicense(it.text("name"), it.text("url")) }
                        .filter { it.name != null || it.url != null },
                properties =
                    project
                        .child("properties")
                        .children()
                        .associate { it.tagName to it.textContent.normalized().orEmpty() },
            )
        }

        private fun documentBuilderFactory(): DocumentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
    }
}

/** A POM with its parents' values filled in ([Pom.effective]): what a library's entry is made of. */
data class EffectivePom(
    val name: String?,
    val description: String?,
    val website: String?,
    val developers: List<String>,
    val licenses: List<PomLicense>,
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

private val PLACEHOLDER = Regex("""\$\{([^}]+)}""")

/** Expansion rounds: properties may refer to properties, but never more than a few levels deep. */
private const val MAX_INTERPOLATION_DEPTH = 5

/** [value] with its `${…}` placeholders filled in from [properties], or null if one stays unknown. */
internal fun interpolate(
    value: String,
    properties: Map<String, String>,
): String? {
    var result = value
    repeat(MAX_INTERPOLATION_DEPTH) {
        if (!PLACEHOLDER.containsMatchIn(result)) return result.normalized()
        result = PLACEHOLDER.replace(result) { match -> properties[match.groupValues[1]] ?: match.value }
    }
    return result.takeUnless { PLACEHOLDER.containsMatchIn(it) }?.normalized()
}

private fun String.isHttp(): Boolean = startsWith("https://") || startsWith("http://")

/** Trimmed, with each run of white space (line breaks and indentation included) as one space; null if blank. */
private fun String.normalized(): String? = trim().replace(WHITE_SPACE, " ").takeIf { it.isNotEmpty() }

private val WHITE_SPACE = Regex("""\s+""")

private fun Element?.children(tag: String? = null): List<Element> {
    if (this == null) return emptyList()
    val nodes = childNodes
    return (0 until nodes.length)
        .map(nodes::item)
        .filterIsInstance<Element>()
        .filter { tag == null || it.tagName == tag }
}

private fun Element.child(tag: String): Element? = children(tag).firstOrNull()

private fun Element.text(tag: String): String? = child(tag)?.textContent?.normalized()
