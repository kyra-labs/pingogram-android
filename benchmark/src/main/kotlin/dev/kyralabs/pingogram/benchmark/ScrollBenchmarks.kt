package dev.kyralabs.pingogram.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiObject2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while the chat list scrolls, the scroll budget in `docs/PERFORMANCE.md` §1. Needs a signed-in app on the
 * device (§9). Each iteration starts the app cold, so it begins on the chat list whatever the last one left open.
 *
 * `CompilationNone` is a fresh install with nothing precompiled; `BaselineProfile` is what users get from the shipped
 * Baseline Profile, so it fails until one has been generated.
 */
@RunWith(AndroidJUnit4::class)
class ChatListScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollCompilationNone() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForChatList()
            },
        ) {
            scrollChatList()
        }
    }
}

/**
 * Frame times while a conversation scrolls through its history, the conversation budget in `docs/PERFORMANCE.md` §1.
 * It scrolls Saved Messages, the one chat the benchmarks open (opening a chat marks its messages as read), so give it
 * a few screens of messages with media for numbers that mean something. Needs a signed-in app on the device (§9).
 */
@RunWith(AndroidJUnit4::class)
class ConversationScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollCompilationNone() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() = scroll(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun scroll(compilationMode: CompilationMode) {
        lateinit var messages: UiObject2
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                messages = openSavedMessages()
            },
        ) {
            scrollConversation(messages)
        }
    }
}

private const val ITERATIONS = 5
