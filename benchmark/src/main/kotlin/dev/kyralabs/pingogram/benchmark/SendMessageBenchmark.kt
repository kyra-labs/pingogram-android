package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sending a text, the "send text message" budget in `docs/PERFORMANCE.md` §1 (≤ 50 ms, optimistic): the app's
 * `sendMessage` trace section, from the tap on send until the conversation has drawn the message's bubble, still on
 * its way to Telegram. It sends to Saved Messages, the one chat the benchmarks open, and deletes each message once its
 * bubble shows (the deleting is outside the section). Needs a signed-in app on the device (§9).
 *
 * The app stays running between iterations, Saved Messages open with the text typed, so each iteration measures the
 * tap alone.
 */
@RunWith(AndroidJUnit4::class)
class SendMessageBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun sendMessageCompilationNone() = sendMessage(CompilationMode.None())

    @Test
    fun sendMessageBaselineProfile() = sendMessage(CompilationMode.Partial(BaselineProfileMode.Require))

    @OptIn(ExperimentalMetricApi::class)
    private fun sendMessage(compilationMode: CompilationMode) {
        var text = ""
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(TraceSectionMetric(SEND_MESSAGE)),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                returnToChatList()
                openSavedMessages()
                text = "Pingogram benchmark ${System.nanoTime()}"
                typeMessage(text)
            },
        ) {
            deleteMessage(sendTyped(text))
        }
    }
}

// :core:ui's SendMessageTrace.SECTION.
private const val SEND_MESSAGE = "sendMessage"

private const val ITERATIONS = 10
