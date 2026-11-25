package dev.kyralabs.pingogram.benchmark

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The memory of the chat list left idle (`docs/PERFORMANCE.md` §1, "Memory, chat list idle"): the app started cold
 * with its Baseline Profile, the chat list drawn, then left on screen for [IDLE_MILLIS] with nothing touched.
 * [MemoryUsageMetric] reads the process's counters at the end of each run from the trace: its anonymous and
 * file-backed RSS and its Java heap (`memoryRssAnonLastKb`, `memoryRssFileLastKb`, `memoryHeapSizeLastKb`).
 * androidx.benchmark has no PSS metric, so the budget's PSS is still read with `dumpsys meminfo` (§9); these
 * figures go into the device's baseline, and `compare.py` fails a run more than 10% above it, as it does for times.
 */
@RunWith(AndroidJUnit4::class)
class ChatListIdleMemoryBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun idle() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics =
                listOf(
                    MemoryUsageMetric(
                        MemoryUsageMetric.Mode.Last,
                        listOf(
                            MemoryUsageMetric.SubMetric.RssAnon,
                            MemoryUsageMetric.SubMetric.RssFile,
                            MemoryUsageMetric.SubMetric.HeapSize,
                        ),
                    ),
                ),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            waitForChatList()
            device.waitForIdle()
            SystemClock.sleep(IDLE_MILLIS)
        }

    private companion object {
        /** Long enough for the start's work to settle: the first pages, avatars and the update router's batches. */
        const val IDLE_MILLIS = 10_000L
        const val ITERATIONS = 5
    }
}
