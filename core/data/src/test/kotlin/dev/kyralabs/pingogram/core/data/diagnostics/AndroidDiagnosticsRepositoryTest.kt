package dev.kyralabs.pingogram.core.data.diagnostics

import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.domain.model.CrashReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The feedback report from what is on the device. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDiagnosticsRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val dispatcher = StandardTestDispatcher()
    private val crash get() = dir.resolve("diagnostics/last_crash.txt")
    private val log get() = dir.resolve("tdlib/logs/tdlib.log")

    private fun repository() =
        AndroidDiagnosticsRepository(
            crashFile = crash,
            tdlibLog = log,
            app = AppInfo("0.5.0", 5_000_002, "gms", isDebug = false),
            device = "Example Phone 9, Android 16 (API 36), arm64-v8a",
            now = { 42L },
            io = dispatcher,
        )

    @Test
    fun `describes the build and the device, without a crash or a log`() =
        runTest(dispatcher) {
            val report = repository().report()

            assertEquals("Pingogram 0.5.0 (5000002), gms, release", report.app)
            assertEquals("Example Phone 9, Android 16 (API 36), arm64-v8a", report.device)
            assertEquals(42L, report.generatedAtMillis)
            assertNull(report.crash)
            assertEquals(emptyList<String>(), report.log)
        }

    @Test
    fun `reads the kept crash, and forgets it when asked`() =
        runTest(dispatcher) {
            crash.parentFile.mkdirs()
            crash.writeText("time=1000\nthread=main\njava.lang.IllegalStateException\n\tat a.B.c(B.kt:1)\n")
            val repository = repository()

            assertEquals(
                CrashReport(1000L, "main", "java.lang.IllegalStateException\n\tat a.B.c(B.kt:1)"),
                repository.report().crash,
            )

            repository.clearCrash()
            assertNull(repository.report().crash)
        }

    @Test
    fun `keeps the last lines of TDLib's log, redacted`() =
        runTest(dispatcher) {
            log.parentFile.mkdirs()
            log.writeText((1..250).joinToString("\n") { "[ 1][t 1][1.0][A.cpp:$it] text = \"message $it\"" } + "\n")

            val lines = repository().report().log

            assertEquals(200, lines.size)
            assertEquals("[ 1][t 1][1.0][A.cpp:51] text = [redacted]", lines.first())
            assertEquals("[ 1][t 1][1.0][A.cpp:250] text = [redacted]", lines.last())
        }
}
