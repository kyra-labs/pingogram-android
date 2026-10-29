package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold and warm start of the launcher activity, the startup budgets in `docs/PERFORMANCE.md` §1, with the app's own
 * startup sections (§2): Hilt's injection of the application (`app.inject`, cold starts only), the activity's
 * `onCreate` (`main.onCreate`) and the wait for the settings behind the splash screen (`main.awaitSettings`).
 *
 * `CompilationNone` is the worst case, a fresh install with nothing precompiled. `BaselineProfile` is what users
 * get from the shipped Baseline Profile, so it fails until one has been generated (`:app:generateBaselineProfile`).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupCompilationNone() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() = startup(StartupMode.COLD, baselineProfile())

    @Test
    fun warmStartupCompilationNone() = startup(StartupMode.WARM, CompilationMode.None())

    @Test
    fun warmStartupBaselineProfile() = startup(StartupMode.WARM, baselineProfile())

    @OptIn(ExperimentalMetricApi::class)
    private fun metrics(startupMode: StartupMode) =
        buildList<Metric> {
            add(StartupTimingMetric())
            if (startupMode == StartupMode.COLD) add(TraceSectionMetric(APP_INJECT))
            add(TraceSectionMetric(ACTIVITY_CREATE))
            add(TraceSectionMetric(AWAIT_SETTINGS))
        }

    private fun baselineProfile() = CompilationMode.Partial(BaselineProfileMode.Require)

    private fun startup(
        startupMode: StartupMode,
        compilationMode: CompilationMode,
    ) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = metrics(startupMode),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            // Time to full display ends with the frame after reportFullyDrawn, which the trace must still hold: on a
            // slow phone that frame was still drawing when startActivityAndWait returned and the trace stopped.
            waitForChatList()
            device.waitForIdle()
        }
    }
}

// The sections of :app's StartupTrace.
private const val APP_INJECT = "app.inject"
private const val ACTIVITY_CREATE = "main.onCreate"
private const val AWAIT_SETTINGS = "main.awaitSettings"
