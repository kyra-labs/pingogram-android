package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Finds the POMs of an app variant's Maven dependencies, and their parent POMs, for [OpenSourceLicensesTask].
 *
 * How, and why this way (`docs/adr/0002-open-source-licenses.md`):
 * - The dependencies are the module components of the variant's runtime classpath graph
 *   (`incoming.resolutionResult.rootComponent`), without our own projects, BOMs and the platform modules of Kotlin
 *   Multiplatform libraries ([mavenComponents]).
 * - Most of them publish Gradle Module Metadata, so Gradle never downloads their POMs while resolving the graph. Each
 *   POM is asked for as an artifact, `group:artifact:version@pom`, of a detached configuration: Gradle downloads it
 *   from the declared repositories into its own cache (or finds it there), verified like any other artifact, and
 *   nothing else goes to the network. An artifact-only dependency takes the file by its name, whatever variants the
 *   module's metadata lists, so Kotlin Multiplatform modules need no attributes. The view is lenient, so one missing
 *   POM does not hide the others; a missing one ends up as a [MavenLibrary.problem], which fails the task.
 * - A detached configuration resolves each module once, so the POMs are asked for in rounds that hold one version of
 *   each module: otherwise two versions of a parent (`guava-parent` 32 and 33) would be resolved to the newer one.
 * - Parent POMs are only known once the POMs are read, so they are asked for in further rounds, until no new parent
 *   turns up.
 * - This is how `cashapp/licensee` and AboutLibraries resolve POMs too. It runs while the configuration cache entry
 *   is stored, in the mapping of `rootComponent` that sets [OpenSourceLicensesTask.libraries] (Gradle resolves the
 *   graph then), not in the task action, where resolving a configuration is not allowed with the configuration cache
 *   (nor is `ArtifactResolutionQuery`). The task gets only the libraries' values, so its cache key holds no absolute
 *   paths and is the same on every machine.
 */
class PomResolver(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val logger: Logger,
) {
    /** The Maven libraries of the graph under [root]. */
    fun resolve(root: ResolvedComponentResult): List<MavenLibrary> {
        val components = mavenComponents(root)
        val poms = mutableMapOf<MavenCoordinates, Pom>()
        val unreadable = mutableMapOf<MavenCoordinates, String>()
        val asked = mutableSetOf<MavenCoordinates>()
        var wanted: Set<MavenCoordinates> = components.toSet()
        var rounds = 0
        while (wanted.isNotEmpty()) {
            check(++rounds <= MAX_PARENT_DEPTH) { "Parent POMs nest deeper than $MAX_PARENT_DEPTH: ${wanted.first()}" }
            asked += wanted
            fetch(wanted).forEach { (coordinates, file) ->
                try {
                    poms[coordinates] = Pom.parse(file.readText(), "The POM of $coordinates")
                } catch (e: IllegalArgumentException) {
                    unreadable[coordinates] = e.message.orEmpty()
                }
            }
            // Each POM is asked for once, found or not.
            wanted = wanted.mapNotNull { poms[it]?.parent }.filterTo(mutableSetOf()) { it !in asked }
        }
        return components.map { coordinates -> library(coordinates, poms, unreadable) }
    }

    private fun library(
        coordinates: MavenCoordinates,
        poms: Map<MavenCoordinates, Pom>,
        unreadable: Map<MavenCoordinates, String>,
    ): MavenLibrary {
        val pom =
            poms[coordinates]
                ?: return MavenLibrary(
                    coordinates,
                    pom = null,
                    problem =
                        unreadable[coordinates]
                            ?: "its POM could not be resolved from the declared repositories",
                )
        val parents = mutableListOf<Pom>()
        var parent = pom.parent
        var problem: String? = null
        while (parent != null) {
            val next = poms[parent]
            if (next == null) {
                problem = unreadable[parent] ?: "its parent POM $parent could not be resolved"
                break
            }
            parents += next
            parent = next.parent
        }
        return MavenLibrary(coordinates, pom.effective(parents), problem)
    }

    /** The POM files of [wanted] that the repositories have, in rounds of one version per module ([PomResolver]). */
    private fun fetch(wanted: Set<MavenCoordinates>): Map<MavenCoordinates, File> {
        val found = mutableMapOf<MavenCoordinates, File>()
        versionRounds(wanted).forEach { round -> found += fetchRound(round).filterKeys { it in wanted } }
        val missing = wanted.filterNot(found::containsKey)
        if (missing.isNotEmpty()) logger.info("No POM found for ${missing.joinToString()}")
        return found
    }

    private fun fetchRound(round: List<MavenCoordinates>): Map<MavenCoordinates, File> {
        val configuration =
            configurations.detachedConfiguration(*round.map { dependencies.create("$it@pom") }.toTypedArray())
        configuration.isTransitive = false
        return configuration.incoming
            .artifactView { lenient(true) }
            .artifacts
            .mapNotNull { artifact ->
                val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                if (id == null || artifact.file.extension != "pom") return@mapNotNull null
                MavenCoordinates(id.group, id.module, id.version) to artifact.file
            }.toMap()
    }

    private companion object {
        /** Parent POMs are rarely more than three deep (`guava` → `guava-parent` → `oss-parent`). */
        const val MAX_PARENT_DEPTH = 10
    }
}

/**
 * [coordinates] split into lists that hold each module once: the first version of every module, then the second, and
 * so on.
 */
internal fun versionRounds(coordinates: Collection<MavenCoordinates>): List<List<MavenCoordinates>> {
    val versions = coordinates.distinct().groupBy(MavenCoordinates::id).values
    return (0 until (versions.maxOfOrNull { it.size } ?: 0)).map { i -> versions.mapNotNull { it.getOrNull(i) } }
}

/**
 * The external module components in the graph under [root], in the graph's order, except BOMs and the platform modules
 * of Kotlin Multiplatform libraries: `androidx.compose.ui:ui` redirects to `ui-android` (Gradle Module Metadata's
 * `available-at`), which holds the code; the library is listed once, by the module people declare.
 */
internal fun mavenComponents(root: ResolvedComponentResult): List<MavenCoordinates> {
    val seen = mutableSetOf(root.id)
    val queue = ArrayDeque(listOf(root))
    val modules = mutableListOf<Pair<ModuleComponentIdentifier, MavenCoordinates>>()
    val redirectTargets = mutableSetOf<ComponentIdentifier>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        val id = component.id
        if (id is ModuleComponentIdentifier && !component.isPlatform()) {
            modules += id to MavenCoordinates(id.group, id.module, id.version)
        }
        component.variants.forEach { variant -> variant.externalVariant.ifPresent { redirectTargets += it.owner } }
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .filterNot { it.isConstraint }
            .map { it.selected }
            .filter { seen.add(it.id) }
            .forEach(queue::addLast)
    }
    return modules.filterNot { (id, _) -> id in redirectTargets }.map { it.second }
}

/** Whether every variant of this component in the graph is a platform (a BOM), which puts nothing into the app. */
private fun ResolvedComponentResult.isPlatform(): Boolean =
    variants.isNotEmpty() &&
        variants.all { variant ->
            val category = variant.attributes.keySet().firstOrNull { it.name == CATEGORY_ATTRIBUTE }
            category != null && variant.attributes.getAttribute(category).toString() in PLATFORM_CATEGORIES
        }

private const val CATEGORY_ATTRIBUTE = "org.gradle.category"
private val PLATFORM_CATEGORIES = setOf("platform", "enforced-platform")
