package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold and warm start of the launcher activity, the startup budgets in `docs/PERFORMANCE.md` §1.
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

    private fun baselineProfile() = CompilationMode.Partial(BaselineProfileMode.Require)

    private fun startup(
        startupMode: StartupMode,
        compilationMode: CompilationMode,
    ) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }
}
