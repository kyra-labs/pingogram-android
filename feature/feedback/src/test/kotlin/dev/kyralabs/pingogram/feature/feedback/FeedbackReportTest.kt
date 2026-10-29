package dev.kyralabs.pingogram.feature.feedback

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URLDecoder

/** The report's text and the GitHub issue it fills. */
class FeedbackReportTest {
    private val report = FeedbackSamples.report.copy(device = "Example Phone 9, Android 16 (API 36), arm64-v8a")

    @Test
    fun `holds the build, the device, the description, the crash and the log`() {
        val text = report.toText("  It froze  ")

        assertTrue(text.startsWith("Pingogram feedback report\nMade: 2026-10-22T"))
        assertTrue(text.contains("App: Pingogram 0.5.0 (5000002), gms, release"))
        assertTrue(text.contains("Device: Example Phone 9, Android 16 (API 36), arm64-v8a"))
        assertTrue(text.contains("What happened:\nIt froze\n"))
        assertTrue(text.contains("Last crash: 2026-10-22T12:00:00Z on thread main\njava.lang.IllegalStateException"))
        assertTrue(text.endsWith(report.log.last()))
    }

    @Test
    fun `says what is missing`() {
        val text = report.copy(crash = null, log = persistentListOf()).toText("")

        assertTrue(text.contains("What happened:\n(not described)"))
        assertTrue(text.contains("Last crash: none kept"))
        assertTrue(text.endsWith("TDLib log: empty"))
    }

    @Test
    fun `fills the bug report form's fields`() {
        val fields = query(report.issueUrl("It froze"))

        assertEquals("bug_report.yml", fields["template"])
        assertEquals("0.5.0 (5000002), gms, release", fields["version"])
        assertEquals("Example Phone 9, Android 16 (API 36), arm64-v8a", fields["device"])
        assertEquals("It froze", fields["steps"])
        assertTrue(fields.getValue("logs").startsWith("Last crash: "))
        assertTrue(fields.getValue("logs").endsWith(report.log.last()))
    }

    @Test
    fun `keeps the address short enough, dropping the log's oldest lines first`() {
        val long = (1..400).map { "[ 1][t 1][1.0][Long.cpp:$it] " + "x".repeat(200) }.toImmutableList()

        val url = report.copy(log = long).issueUrl("y".repeat(5000))

        assertTrue(url.length <= MAX_URL_LENGTH)
        val logs = query(url).getValue("logs")
        assertTrue(logs.endsWith(long.last()))
        assertFalse(logs.contains(long.first()))
    }

    private fun query(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate {
            val (key, value) = it.split('=', limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8.name())
        }
}
