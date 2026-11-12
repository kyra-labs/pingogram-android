package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.RecompositionCounter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The record button's colors cross-fade in the draw phase (plan A-5, M-034): starting or ending a recording recomposes
 * the button as its face and the fade start and end, never in the frames between.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RecordButtonRecompositionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val counter = RecompositionCounter()
    private var recording by mutableStateOf<VoiceRecordingUiState?>(null)
    private var shown by mutableStateOf(false)

    @Test
    fun `starting a recording recomposes the button a few times, not in every frame of its fade`() {
        setButton()

        recording = VoiceRecordingUiState()
        repeat(FRAMES) { frame() }

        val button = counter.recomposedOwnFunctions.count { it.endsWith(BUTTON) }
        assertTrue("$button: ${counter.recomposedOwnFunctions}", button in 1..AT_MOST)
    }

    @Test
    fun `ending a recording recomposes the button a few times, not in every frame of its fade`() {
        recording = VoiceRecordingUiState()
        setButton()

        recording = null
        repeat(FRAMES) { frame() }

        val button = counter.recomposedOwnFunctions.count { it.endsWith(BUTTON) }
        assertTrue("$button: ${counter.recomposedOwnFunctions}", button in 1..AT_MOST)
    }

    private fun setButton() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                if (shown) RecordButton(recording = recording, drag = RecordDrag(), onEvent = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        counter.start()
        shown = true
        repeat(FRAMES) { frame() }
        counter.reset()
    }

    private fun frame() {
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private companion object {
        const val FRAMES = 40
        const val BUTTON = "RecordButtonKt.RecordButton"

        /** The face changing, and the fade starting and ending. */
        const val AT_MOST = 3
    }
}
