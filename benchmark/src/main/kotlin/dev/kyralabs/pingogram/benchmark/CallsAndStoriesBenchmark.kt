package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while Recent calls (the Contacts tab's top bar) and the story rings above the chat list scroll. Neither
 * places a call nor opens a story ([scrollRecentCalls], [scrollStoryRings]). Recent calls needs calls on the account,
 * the rings stories from its contacts or channels; each test fails saying so where they are missing. Needs a signed-in
 * app on a phone, where the navigation bar shows the Contacts tab (`docs/PERFORMANCE.md` §9).
 */
@RunWith(AndroidJUnit4::class)
class CallsAndStoriesBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun recentCallsCompilationNone() = recentCalls(CompilationMode.None())

    @Test
    fun recentCallsBaselineProfile() = recentCalls(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun storyRingsCompilationNone() = storyRings(CompilationMode.None())

    @Test
    fun storyRingsBaselineProfile() = storyRings(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun recentCalls(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                coldStart()
                openRecentCalls() ?: error("No Recent calls list. Use a phone, and an account with calls.")
            },
        ) {
            scrollRecentCalls()
        }
    }

    private fun storyRings(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                coldStart()
                storyRings() ?: error("No story rings. Use an account whose contacts or channels post stories.")
            },
        ) {
            scrollStoryRings()
        }
    }
}

private const val ITERATIONS = 5
