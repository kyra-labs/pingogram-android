package dev.kyralabs.pingogram.feature.chat.ui.videonote

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.voice.CameraRationale
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordModeUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VideoRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.previewMeter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of round video messages in the conversation (`docs/adr/0032-round-video-messages.md`, `docs/DESIGN_SYSTEM.md`
 * §6 Composer), every state in the looks the audit asks for (§12): light, dark, true black, high contrast, right to
 * left, a medium window and 200% font scale. The record button switched to the camera; the rationale before Android
 * asks, and once it no longer does; a recording held (the round preview with its ring on the placeholder frame, as
 * Robolectric has no camera, over the dimmed messages), locked with "Flip camera", and stopped as the screen stopped.
 * Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class VideoNoteScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val (mode, recording) = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(
                    state = ChatPreviewData.privateChat,
                    onEvent = {},
                    onBack = {},
                    recording = recording?.copy(meter = previewMeter()),
                    recordMode = mode,
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        private val VIDEO = RecordModeUiState(video = true, canSwitch = true)

        val STATES: Map<String, Pair<RecordModeUiState, VoiceRecordingUiState?>> =
            mapOf(
                "camera" to (VIDEO to null),
                "rationale" to (RecordModeUiState(canSwitch = true, rationale = CameraRationale()) to null),
                "rationaleDenied" to
                    (RecordModeUiState(canSwitch = true, rationale = CameraRationale(deniedForGood = true)) to null),
                "held" to (VIDEO to VoiceRecordingUiState(video = VideoRecordingUiState(canFlip = true))),
                "locked" to
                    (VIDEO to VoiceRecordingUiState(isLocked = true, video = VideoRecordingUiState(canFlip = true))),
                "stopped" to
                    (
                        VIDEO to
                            VoiceRecordingUiState(
                                isLocked = true,
                                isPaused = true,
                                video = VideoRecordingUiState(isStopped = true),
                            )
                    ),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
