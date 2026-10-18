package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

class TdlibAarTest {
    private val commit = "a94b83d67ab4c8397c6245e541b31a4131ea2f79"
    private val aarBytes = "a TDLib AAR".toByteArray()
    private val pin = MessageDigest.getInstance("SHA-256").digest(aarBytes).joinToString("") { "%02x".format(it) }

    private fun failure(block: () -> Unit): String = assertThrows<IllegalStateException>(block).message.orEmpty()

    @Nested
    inner class Plan {
        private fun plan(
            pinnedSha256: String = pin,
            overrideAar: File? = null,
            installedSha256: String? = null,
        ) = TdlibAar.plan(commit, pinnedSha256, overrideAar) { installedSha256 }

        @Test
        fun `a missing AAR is downloaded`() {
            assertTrue(plan(installedSha256 = null) is TdlibAar.Plan.Download)
        }

        @Test
        fun `an AAR that does not match the pin is downloaded again`() {
            assertTrue(plan(installedSha256 = "0".repeat(64)) is TdlibAar.Plan.Download)
        }

        @Test
        fun `an AAR that matches the pin is up to date, whatever the case of the hex digits`() {
            assertEquals(TdlibAar.Plan.UpToDate, plan(pinnedSha256 = pin.uppercase(), installedSha256 = pin))
        }

        @Test
        fun `an override is used without looking at the pin or the installed AAR`(
            @TempDir dir: File,
        ) {
            val override = dir.resolve("tdlib.aar").apply { writeBytes(aarBytes) }

            val plan = TdlibAar.plan(commit, TdlibAar.UNPINNED, override) { error("must not hash the installed AAR") }

            assertEquals(TdlibAar.Plan.UseOverride(override), plan)
        }

        @Test
        fun `an override that is not a file fails, naming the property`(
            @TempDir dir: File,
        ) {
            val message = failure { plan(overrideAar = dir.resolve("missing.aar")) }

            assertTrue(TdlibAar.OVERRIDE_PROPERTY in message && "missing.aar" in message, message)
        }

        @Test
        fun `an unpinned checksum fails with where to find it and how to build without it`() {
            val message = failure { plan(pinnedSha256 = TdlibAar.UNPINNED) }

            assertTrue(
                listOf(
                    TdlibAar.SHA256_CATALOG_KEY,
                    "https://github.com/kyra-labs/pingogram-android/releases/tag/tdlib-a94b83d67a",
                    "SHA256SUMS.txt",
                    TdlibAar.OVERRIDE_PROPERTY,
                    "docs/TDLIB.md",
                ).all { it in message },
                message,
            )
        }

        @Test
        fun `a blank checksum fails like an unpinned one`() {
            val message = failure { plan(pinnedSha256 = " ") }

            assertTrue("not pinned" in message, message)
        }

        @Test
        fun `a checksum that is not a SHA-256 fails`() {
            val message = failure { plan(pinnedSha256 = "abc123") }

            assertTrue("64 hexadecimal characters" in message, message)
        }
    }

    @Test
    fun `the download URL is the AAR asset of the release for the commit`() {
        assertEquals(
            URI(
                "https://github.com/kyra-labs/pingogram-android/releases/download/tdlib-a94b83d67a/tdlib-a94b83d67a.aar",
            ),
            TdlibAar.downloadUrl(commit),
        )
    }

    @Nested
    inner class Ensure {
        @TempDir
        lateinit var repository: File

        private val downloads = mutableListOf<URI>()

        private fun ensure(
            pinnedSha256: String = pin,
            overrideAar: File? = null,
            offline: Boolean = false,
            download: (URI, File) -> Unit = { uri, destination ->
                downloads += uri
                destination.writeBytes(aarBytes)
            },
        ) = TdlibAar.ensure(repository, commit, pinnedSha256, overrideAar, offline, log = {}, download = download)

        private fun installedAar(version: String) =
            repository.resolve("org/drinkless/tdlib/$version/tdlib-$version.aar")

        private fun installedPom(version: String) =
            repository.resolve("org/drinkless/tdlib/$version/tdlib-$version.pom")

        @Test
        fun `a download is installed as a Maven artifact with a POM`() {
            val version = ensure()

            val pom = installedPom(version).readText()
            assertTrue(
                version == commit &&
                    installedAar(version).readBytes().contentEquals(aarBytes) &&
                    "<packaging>aar</packaging>" in pom,
                pom,
            )
        }

        @Test
        fun `an installed AAR that matches the pin is not downloaded again`() {
            ensure()

            ensure()

            assertEquals(1, downloads.size)
        }

        @Test
        fun `a modified AAR is downloaded again`() {
            val version = ensure()
            installedAar(version).writeText("tampered")

            ensure()

            assertEquals(2, downloads.size)
        }

        @Test
        fun `a download that does not match the pin is rejected and not installed`() {
            val message = failure { ensure(download = { _, destination -> destination.writeText("something else") }) }

            assertTrue(
                "SHA-256" in message && !installedAar(commit).exists(),
                message,
            )
        }

        @Test
        fun `a failed download names the release to check and the override`() {
            val message = failure { ensure(download = { _, _ -> throw IOException("HTTP 404 Not Found") }) }

            assertTrue(
                listOf("HTTP 404", "tdlib-a94b83d67a", TdlibAar.OVERRIDE_PROPERTY).all { it in message },
                message,
            )
        }

        @Test
        fun `offline builds fail instead of downloading`() {
            val message = failure { ensure(offline = true) }

            assertTrue("offline" in message && downloads.isEmpty(), message)
        }

        @Test
        fun `an override is copied to a local version, without download`(
            @TempDir dir: File,
        ) {
            val override = dir.resolve("self-built.aar").apply { writeText("self-built") }

            val version = ensure(pinnedSha256 = TdlibAar.UNPINNED, overrideAar = override)

            assertTrue(
                version == "$commit-local" &&
                    installedAar(version).readText() == "self-built" &&
                    downloads.isEmpty(),
            )
        }

        @Test
        fun `a changed override is copied again`(
            @TempDir dir: File,
        ) {
            val override = dir.resolve("self-built.aar").apply { writeText("first") }
            ensure(overrideAar = override)
            override.writeText("second build")

            val version = ensure(overrideAar = override)

            assertEquals("second build", installedAar(version).readText())
        }

        @Test
        fun `no temporary files are left behind`() {
            ensure()

            val leftovers = repository.walk().filter { it.isFile && it.name.endsWith(".tmp") }.toList()

            assertFalse(leftovers.isNotEmpty(), leftovers.toString())
        }
    }
}
