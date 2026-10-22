package dev.kyralabs.pingogram.buildlogic

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * A native library that a workflow of this repository builds from a pinned commit and publishes as an AAR: TDLib
 * ([TDLIB], `docs/TDLIB.md`) and the call engine NTgCalls ([NTGCALLS], `docs/adr/0015-call-media-engine.md`).
 *
 * The workflow publishes it as the GitHub release `<releasePrefix>-<first 10 characters of the commit>`. The build
 * installs it into a small file-based Maven repository, [repository] (git-ignored), which settings.gradle.kts declares
 * for [group] only. A repository rather than a `files("….aar")` dependency, because AGP refuses direct local `.aar`
 * dependencies in library modules when it bundles their AAR.
 *
 * The download is verified against the SHA-256 pinned in the version catalog ([sha256CatalogKey]). The Gradle
 * property [overrideProperty] replaces it with a self-built AAR, without download or checksum.
 */
class PrebuiltAar(
    /** What the messages call it. */
    val label: String,
    val group: String,
    val name: String,
    /** Relative to the repository root; settings.gradle.kts declares the same path. */
    val repository: String,
    /** Version catalog entries: the upstream commit, and the SHA-256 of the released AAR. */
    val commitCatalogKey: String,
    val sha256CatalogKey: String,
    /** Gradle property with the path of a self-built AAR (absolute, or relative to the repository root). */
    val overrideProperty: String,
    private val releasePrefix: String,
    /** The workflow that publishes the release, and where to read more. */
    private val workflow: String,
    private val docs: String,
    private val license: String,
) {
    fun releaseTag(commit: String): String = "$releasePrefix-${commit.take(SHORT_COMMIT_LENGTH)}"

    fun downloadUrl(commit: String): URI = URI("$RELEASES/download/${releaseTag(commit)}/${releaseTag(commit)}.aar")

    /** Maven version of the installed AAR: the commit, marked when it is a local override. */
    fun version(
        commit: String,
        isOverride: Boolean,
    ): String = if (isOverride) "$commit-local" else commit

    /** What to do to get the AAR in place. */
    sealed interface Plan {
        /** Copy the self-built [aar] from the override property; no download, no checksum. */
        data class UseOverride(
            val aar: File,
        ) : Plan

        /** The installed AAR matches the pinned checksum. */
        data object UpToDate : Plan

        data class Download(
            val reason: String,
        ) : Plan
    }

    /**
     * Decides how to get the AAR, or throws [IllegalStateException] with what to fix. [installedSha256] is the
     * SHA-256 of the AAR currently installed, or null if there is none; it is only computed when needed.
     */
    fun plan(
        commit: String,
        pinnedSha256: String,
        overrideAar: File?,
        installedSha256: () -> String?,
    ): Plan {
        if (overrideAar != null) {
            check(overrideAar.isFile) {
                "The Gradle property $overrideProperty is set to $overrideAar, which is not a file. Point it at a " +
                    "$label AAR (absolute path, or relative to the repository root), or remove it to download the " +
                    "pinned release. See $docs."
            }
            return Plan.UseOverride(overrideAar)
        }
        check(pinnedSha256.isNotBlank() && pinnedSha256 != UNPINNED) {
            "The $label AAR checksum is not pinned: $sha256CatalogKey in gradle/libs.versions.toml is " +
                "'$pinnedSha256'. Set it " +
                "to the SHA-256 of ${releaseTag(commit)}.aar from SHA256SUMS.txt in the release " +
                "$RELEASES/tag/${releaseTag(commit)} (published by the $workflow workflow), or use a $label AAR you " +
                "built yourself with the Gradle property $overrideProperty=/absolute/path/to/$name.aar " +
                "(e.g. in ~/.gradle/gradle.properties). See $docs."
        }
        check(SHA256.matches(pinnedSha256)) {
            "$sha256CatalogKey in gradle/libs.versions.toml must be a SHA-256 (64 hexadecimal characters), " +
                "but it is '$pinnedSha256'."
        }
        val installed = installedSha256() ?: return Plan.Download("not downloaded yet")
        return if (installed.equals(pinnedSha256, ignoreCase = true)) {
            Plan.UpToDate
        } else {
            Plan.Download("the installed AAR does not match $sha256CatalogKey")
        }
    }

    /**
     * Makes sure the AAR for [commit] is installed under [repository] and returns its Maven version. Downloads it
     * with [download] when it is missing or does not match [pinnedSha256], unless [overrideAar] is set. Safe to call
     * from concurrent builds: the work happens under a file lock.
     */
    fun ensure(
        repository: File,
        commit: String,
        pinnedSha256: String,
        overrideAar: File?,
        offline: Boolean,
        log: (String) -> Unit,
        download: (URI, File) -> Unit = ::httpDownload,
    ): String {
        val version = version(commit, isOverride = overrideAar != null)
        val installation = Installation(repository, version)
        withLock(repository) {
            val plan = plan(commit, pinnedSha256, overrideAar) { installation.aar.takeIf(File::isFile)?.let(::sha256) }
            if (plan is Plan.Download) {
                val url = downloadUrl(commit)
                check(!offline) {
                    "The $label AAR is needed (${plan.reason}), but Gradle runs offline. Run once without " +
                        "--offline to download $url, or set $overrideProperty. See $docs."
                }
                log("Downloading the $label AAR (${plan.reason}) from $url")
                installation.download(url, releaseTag(commit), pinnedSha256, download)
            } else if (plan is Plan.UseOverride) {
                installation.copyFrom(plan.aar)
            }
            installation.writePom()
        }
        return version
    }

    companion object {
        /** [sha256CatalogKey] before the release exists; version catalogs allow no empty values. */
        const val UNPINNED = "unpinned"

        val TDLIB =
            PrebuiltAar(
                label = "TDLib",
                group = "org.drinkless",
                name = "tdlib",
                repository = "core/tdlib/libs/maven",
                commitCatalogKey = "tdlib",
                sha256CatalogKey = "tdlibAarSha256",
                overrideProperty = "pingogram.tdlib.aar",
                releasePrefix = "tdlib",
                workflow = "Build TDLib",
                docs = "docs/TDLIB.md",
                license = "TDLib for Android, Boost Software License 1.0",
            )

        val NTGCALLS =
            PrebuiltAar(
                label = "NTgCalls",
                group = "io.github.pytgcalls",
                name = "ntgcalls",
                repository = "feature/calls/libs/maven",
                commitCatalogKey = "ntgcalls",
                sha256CatalogKey = "ntgcallsAarSha256",
                overrideProperty = "pingogram.ntgcalls.aar",
                releasePrefix = "ntgcalls",
                workflow = "Build NTgCalls",
                docs = "docs/adr/0015-call-media-engine.md",
                license = "NTgCalls for Android, GNU Lesser General Public License v3.0",
            )

        private val ALL = listOf(TDLIB, NTGCALLS)

        /** The AAR whose [name] is [name]; what the configuration cache keeps of a [PrebuiltAar]. */
        fun named(name: String): PrebuiltAar = ALL.single { it.name == name }

        private const val RELEASES = "https://github.com/kyra-labs/pingogram-android/releases"
        private const val SHORT_COMMIT_LENGTH = 10
        private val SHA256 = Regex("[0-9a-fA-F]{64}")

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(file.inputStream().buffered(), digest).use { it.copyTo(OutputStream.nullOutputStream()) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** Fetches [uri] (following GitHub's redirect to its asset host) into [destination]. */
        fun httpDownload(
            uri: URI,
            destination: File,
        ) {
            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "pingogram-build")
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status ${connection.responseMessage}")
                connection.inputStream.use { input -> destination.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
        }

        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 120_000

        /** Serializes installs across threads of this JVM and across processes (an IDE sync next to a command line build). */
        private fun withLock(
            repository: File,
            action: () -> Unit,
        ) {
            repository.mkdirs()
            synchronized(this) {
                FileChannel
                    .open(
                        repository.resolve(".lock").toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                    ).use {
                        it.lock().use { action() }
                    }
            }
        }
    }

    /** Maven layout of one version: `<group as path>/<name>/<version>/<name>-<version>.{aar,pom}`. */
    private inner class Installation(
        repository: File,
        private val version: String,
    ) {
        private val directory = repository.resolve("${group.replace('.', '/')}/$name/$version")
        val aar: File = directory.resolve("$name-$version.aar")
        private val pom: File = directory.resolve("$name-$version.pom")

        fun copyFrom(source: File) {
            // Copying keeps the modification time, so an unchanged override is not copied again.
            if (aar.isFile && aar.length() == source.length() && aar.lastModified() == source.lastModified()) return
            directory.mkdirs()
            val temporary = File.createTempFile("$name-", ".tmp", directory)
            try {
                Files.copy(
                    source.toPath(),
                    temporary.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES,
                )
                moveIntoPlace(temporary, aar)
            } finally {
                temporary.delete()
            }
        }

        fun download(
            url: URI,
            releaseTag: String,
            pinnedSha256: String,
            download: (URI, File) -> Unit,
        ) {
            directory.mkdirs()
            val temporary = File.createTempFile("$name-", ".tmp", directory)
            try {
                try {
                    download(url, temporary)
                } catch (e: IOException) {
                    throw IllegalStateException(
                        "Could not download the $label AAR from $url (${e.message}). Check that the $workflow " +
                            "workflow has published the release $releaseTag (docs/OWNER_SETUP.md), or " +
                            "set the Gradle property $overrideProperty to a $label AAR you built. See $docs.",
                        e,
                    )
                }
                val actual = sha256(temporary)
                check(actual.equals(pinnedSha256, ignoreCase = true)) {
                    "The $label AAR downloaded from $url has SHA-256 $actual, but $sha256CatalogKey in " +
                        "gradle/libs.versions.toml pins $pinnedSha256. Fix the pin if the release was rebuilt " +
                        "on purpose; otherwise the download is corrupt or not the published file."
                }
                moveIntoPlace(temporary, aar)
            } finally {
                temporary.delete()
            }
        }

        fun writePom() {
            val text =
                """
                |<?xml version="1.0" encoding="UTF-8"?>
                |<!-- Written by build-logic (PrebuiltAar.kt). $license. -->
                |<project xmlns="http://maven.apache.org/POM/4.0.0">
                |  <modelVersion>4.0.0</modelVersion>
                |  <groupId>$group</groupId>
                |  <artifactId>$name</artifactId>
                |  <version>$version</version>
                |  <packaging>aar</packaging>
                |</project>
                |
                """.trimMargin()
            if (pom.isFile && pom.readText() == text) return
            directory.mkdirs()
            val temporary = File.createTempFile("$name-", ".tmp", directory)
            temporary.writeText(text)
            moveIntoPlace(temporary, pom)
        }

        private fun moveIntoPlace(
            source: File,
            target: File,
        ) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }
}
