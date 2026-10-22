package dev.kyralabs.pingogram.core.data.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The last crash, kept on the device for the feedback report. */
class CrashRecorderTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `keeps the thread, the time and the trace with its causes, messages redacted`() {
        val file = dir.resolve("diagnostics/last_crash.txt")
        val cause = IllegalArgumentException("Bad phone_number = 447911123456")

        CrashRecorder(file) { 1_000L }.record("main", IllegalStateException("Expected \"Ada\"", cause))

        val lines = file.readLines()
        assertEquals("time=1000", lines[0])
        assertEquals("thread=main", lines[1])
        assertEquals("java.lang.IllegalStateException: Expected \"[text]\"", lines[2])
        assertTrue(lines.any { it == "Caused by: java.lang.IllegalArgumentException: Bad phone_number = [redacted]" })
        assertTrue(lines.any { it.startsWith("\tat ") })
    }

    @Test
    fun `a trace that refers to itself ends`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        val trace = CrashRecorder.trace(first)

        assertEquals(2, trace.lines().count { it.contains("RuntimeException") })
    }

    @Test
    fun `a file that cannot be written does not throw`() {
        val blocked = dir.resolve("file").apply { writeText("") }.resolve("last_crash.txt")

        CrashRecorder(blocked) { 0L }.record("main", RuntimeException())

        assertFalse(blocked.exists())
    }
}
