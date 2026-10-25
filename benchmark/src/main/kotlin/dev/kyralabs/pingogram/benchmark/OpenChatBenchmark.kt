package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opening a chat from the chat list, the "open chat" budget in `docs/PERFORMANCE.md` §1 (≤ 150 ms with its history
 * cached): the app's `openChat` trace section, from the tap until the conversation has drawn its first messages. It
 * opens Saved Messages, the one chat the benchmarks open (opening a chat marks its messages as read), whose history
 * TDLib has on the device after the first iteration. Needs a signed-in app on the device (§9).
 *
 * The app stays running between iterations: each one starts on the chat list, coming back from the last one's
 * conversation, so the history is cached as the budget says.
 */
@RunWith(AndroidJUnit4::class)
class OpenChatBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun openChatCompilationNone() = openChat(CompilationMode.None())

    @Test
    fun openChatBaselineProfile() = openChat(CompilationMode.Partial(BaselineProfileMode.Require))

    @OptIn(ExperimentalMetricApi::class)
    private fun openChat(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric(OPEN_CHAT), FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                returnToChatList()
            },
        ) {
            openSavedMessages()
        }
    }
}

// :core:ui's OpenChatTrace.SECTION.
private const val OPEN_CHAT = "openChat"

private const val ITERATIONS = 10
