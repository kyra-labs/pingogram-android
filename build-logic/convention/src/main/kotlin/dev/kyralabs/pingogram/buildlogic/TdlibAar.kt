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
 * The TDLib AAR that `:core:tdlib` builds against (`docs/TDLIB.md`, Build strategy).
 *
 * The Build TDLib workflow publishes it as the GitHub release `tdlib-<first 10 characters of the commit>`. The build
 * installs it into a small file-based Maven repository, [REPOSITORY] (git-ignored), which settings.gradle.kts
 * declares for the group `org.drinkless` only. A repository rather than a `files("….aar")` dependency, because AGP
 * refuses direct local `.aar` dependencies in library modules when it bundles their AAR.
 *
 * The download is verified against the SHA-256 pinned in the version catalog ([SHA256_CATALOG_KEY]). The Gradle
 * property [OVERRIDE_PROPERTY] replaces it with a self-built AAR, without download or checksum.
 */
object TdlibAar {
    const val GROUP = "org.drinkless"
    const val NAME = "tdlib"

    /** Relative to the repository root; settings.gradle.kts declares the same path. */
    const val REPOSITORY = "core/tdlib/libs/maven"

    /** Version catalog entries: the tdlib/td commit, and the SHA-256 of the released AAR. */
    const val COMMIT_CATALOG_KEY = "tdlib"
    const val SHA256_CATALOG_KEY = "tdlibAarSha256"

    /** [SHA256_CATALOG_KEY] before the release exists; version catalogs allow no empty values. */
    const val UNPINNED = "unpinned"

    /** Gradle property with the path of a self-built AAR (absolute, or relative to the repository root). */
    const val OVERRIDE_PROPERTY = "pingogram.tdlib.aar"

    private const val RELEASES = "https://github.com/kyra-labs/pingogram-android/releases"
    private const val SHORT_COMMIT_LENGTH = 10
    private val SHA256 = Regex("[0-9a-fA-F]{64}")

    fun releaseTag(commit: String): String = "tdlib-${commit.take(SHORT_COMMIT_LENGTH)}"

    fun downloadUrl(commit: String): URI = URI("$RELEASES/download/${releaseTag(commit)}/${releaseTag(commit)}.aar")

    /** Maven version of the installed AAR: the commit, marked when it is a local override. */
    fun version(
        commit: String,
        isOverride: Boolean,
    ): String = if (isOverride) "$commit-local" else commit

    /** What to do to get the AAR in place. */
    sealed interface Plan {
        /** Copy the self-built [aar] from [OVERRIDE_PROPERTY]; no download, no checksum. */
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
                "The Gradle property $OVERRIDE_PROPERTY is set to $overrideAar, which is not a file. Point it at a " +
                    "TDLib AAR (absolute path, or relative to the repository root), or remove it to download the " +
                    "pinned release. See docs/TDLIB.md."
            }
            return Plan.UseOverride(overrideAar)
        }
        check(pinnedSha256.isNotBlank() && pinnedSha256 != UNPINNED) {
            "The TDLib AAR checksum is not pinned: $SHA256_CATALOG_KEY in gradle/libs.versions.toml is " +
                "'$pinnedSha256'. Set it " +
                "to the SHA-256 of ${releaseTag(commit)}.aar from SHA256SUMS.txt in the release " +
                "$RELEASES/tag/${releaseTag(commit)} (published by the Build TDLib workflow), or use a TDLib AAR you " +
                "built yourself with the Gradle property $OVERRIDE_PROPERTY=/absolute/path/to/tdlib.aar " +
                "(e.g. in ~/.gradle/gradle.properties). See docs/TDLIB.md."
        }
        check(SHA256.matches(pinnedSha256)) {
            "$SHA256_CATALOG_KEY in gradle/libs.versions.toml must be a SHA-256 (64 hexadecimal characters), " +
                "but it is '$pinnedSha256'."
        }
        val installed = installedSha256() ?: return Plan.Download("not downloaded yet")
        return if (installed.equals(pinnedSha256, ignoreCase = true)) {
            Plan.UpToDate
        } else {
            Plan.Download("the installed AAR does not match $SHA256_CATALOG_KEY")
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
                    "The TDLib AAR is needed (${plan.reason}), but Gradle runs offline. Run once without " +
                        "--offline to download $url, or set $OVERRIDE_PROPERTY. See docs/TDLIB.md."
                }
                log("Downloading the TDLib AAR (${plan.reason}) from $url")
                installation.download(url, releaseTag(commit), pinnedSha256, download)
            } else if (plan is Plan.UseOverride) {
                installation.copyFrom(plan.aar)
            }
            installation.writePom()
        }
        return version
    }

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

    /** Maven layout of one version: `org/drinkless/tdlib/<version>/tdlib-<version>.{aar,pom}`. */
    private class Installation(
        repository: File,
        private val version: String,
    ) {
        private val directory = repository.resolve("${GROUP.replace('.', '/')}/$NAME/$version")
        val aar: File = directory.resolve("$NAME-$version.aar")
        private val pom: File = directory.resolve("$NAME-$version.pom")

        fun copyFrom(source: File) {
            // Copying keeps the modification time, so an unchanged override is not copied again.
            if (aar.isFile && aar.length() == source.length() && aar.lastModified() == source.lastModified()) return
            directory.mkdirs()
            val temporary = File.createTempFile("$NAME-", ".tmp", directory)
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
            val temporary = File.createTempFile("$NAME-", ".tmp", directory)
            try {
                try {
                    download(url, temporary)
                } catch (e: IOException) {
                    throw IllegalStateException(
                        "Could not download the TDLib AAR from $url (${e.message}). Check that the Build TDLib " +
                            "workflow has published the release $releaseTag (docs/OWNER_SETUP.md §6), or " +
                            "set the Gradle property $OVERRIDE_PROPERTY to a TDLib AAR you built. See docs/TDLIB.md.",
                        e,
                    )
                }
                val actual = sha256(temporary)
                check(actual.equals(pinnedSha256, ignoreCase = true)) {
                    "The TDLib AAR downloaded from $url has SHA-256 $actual, but $SHA256_CATALOG_KEY in " +
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
                |<!-- Written by build-logic (TdlibAar.kt). TDLib for Android, Boost Software License 1.0. -->
                |<project xmlns="http://maven.apache.org/POM/4.0.0">
                |  <modelVersion>4.0.0</modelVersion>
                |  <groupId>$GROUP</groupId>
                |  <artifactId>$NAME</artifactId>
                |  <version>$version</version>
                |  <packaging>aar</packaging>
                |</project>
                |
                """.trimMargin()
            if (pom.isFile && pom.readText() == text) return
            directory.mkdirs()
            val temporary = File.createTempFile("$NAME-", ".tmp", directory)
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
