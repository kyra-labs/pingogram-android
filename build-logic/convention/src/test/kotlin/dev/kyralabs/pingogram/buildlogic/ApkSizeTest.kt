package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApkSizeTest {
    @Test
    fun `APKs within their budgets pass`() {
        val sizes = mapOf("arm64-v8a" to 55_000_000L, null to 133_000_000L, "x86_64" to 90_000_000L)

        assertTrue(ApkSize.violations(sizes).isEmpty())
    }

    @Test
    fun `an arm64 APK over 53 MB fails, naming its size and budget`() {
        val violations = ApkSize.violations(mapOf("arm64-v8a" to 56_000_000L, "armeabi-v7a" to 60_000_000L))

        assertEquals(listOf("The arm64-v8a APK is 53.4 MB, over its budget of 53.0 MB"), violations)
    }

    @Test
    fun `a universal APK over 128 MB fails`() {
        assertEquals(1, ApkSize.violations(mapOf(null to 135_000_000L)).size)
    }

    @Test
    fun `the report lists every APK with its budget, in MB of 1,048,576 bytes`() {
        val report = ApkSize.report(mapOf(null to 126_300_000L, "arm64-v8a" to 50_126_706L, "x86_64" to 57_700_000L))

        assertEquals(
            "arm64-v8a: 47.8 MB (budget 53.0 MB)\nuniversal: 120.4 MB (budget 128.0 MB)\nx86_64: 55.0 MB\n",
            report,
        )
    }
}
