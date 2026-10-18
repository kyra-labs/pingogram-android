package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PingogramVersionTest {
    @Test
    fun `missing or blank version falls back to the dev version`() {
        assertEquals(PingogramVersion.DEV_VERSION, PingogramVersion.parse(null).name)
        assertEquals(PingogramVersion.DEV_VERSION, PingogramVersion.parse("  ").name)
    }

    @Test
    fun `release version code follows the documented formula`() {
        assertEquals(1_020_300, PingogramVersion.parse("1.2.3").versionCode())
    }

    @Test
    fun `per-ABI codes are distinct and the universal APK is lowest`() {
        val version = PingogramVersion.parse("0.2.0")

        val codes = listOf(null, "armeabi-v7a", "arm64-v8a", "x86_64").map(version::versionCode)

        assertEquals(listOf(20_000, 20_001, 20_002, 20_003), codes)
    }

    @Test
    fun `pre-release suffix is kept in the name and ignored in the code`() {
        val version = PingogramVersion.parse("1.0.0-beta.1")

        assertEquals("1.0.0-beta.1" to 1_000_000, version.name to version.versionCode())
    }

    @Test
    fun `leading v from a tag is stripped`() {
        assertEquals("0.1.0", PingogramVersion.parse("v0.1.0").name)
    }

    @Test
    fun `dev version still gets a positive version code`() {
        assertEquals(1, PingogramVersion.parse(null).versionCode())
    }

    @Test
    fun `malformed versions are rejected`() {
        listOf("1.2", "latest", "1.2.3.4").forEach { raw ->
            assertThrows<IllegalStateException>(raw) { PingogramVersion.parse(raw) }
        }
    }

    @Test
    fun `versions that overflow the code formula are rejected`() {
        listOf("1.100.0", "1.0.100", "2000.0.0").forEach { raw ->
            assertThrows<IllegalArgumentException>(raw) { PingogramVersion.parse(raw) }
        }
    }

    @Test
    fun `unknown ABI is rejected`() {
        assertThrows<IllegalStateException> { PingogramVersion.parse("1.0.0").versionCode("mips") }
    }
}
