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
 * Frame times while Catch-up's cards change (`docs/DESIGN_SYSTEM.md` §6 "Catch-up"): it skips three cards, which
 * reads no chat. The chip needs at least five unread chats that are not muted, so leave as many unread on the account
 * the device is signed in to. Needs a signed-in app on the device (`docs/PERFORMANCE.md` §9).
 */
@RunWith(AndroidJUnit4::class)
class CatchUpBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun skipCompilationNone() = skip(CompilationMode.None())

    @Test
    fun skipBaselineProfile() = skip(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun skip(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                coldStart()
                openCatchUp() ?: error("No Catch-up chip. Leave at least five chats that are not muted unread.")
            },
        ) {
            skipCatchUpCards()
        }
    }
}

private const val ITERATIONS = 5
