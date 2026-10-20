package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the composer recording a voice message in the conversation on a compact phone, light and dark
 * (`docs/TESTING.md`): held under the finger (the pill with the voice's level, "Slide to cancel" and the lock),
 * locked (Delete, the live waveform, Pause and the send pill) and locked and paused; the locked one also at 200% font
 * scale and right to left. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class VoiceRecordingScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    /** At 200% font scale the bar grows instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "locked")
        capture("fontScale200", ThemeMode.Light)
    }

    /**
     * Right to left, the waveform's newest bar is at the left end and "Slide to cancel" points right. The layout
     * direction is provided directly: the test activity's manifest does not declare RTL support.
     */
    @Test
    fun rtl() {
        assumeTrue(stateName != "paused")
        capture("rtl", ThemeMode.Light, LayoutDirection.Rtl)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val recording = STATES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    ChatScreen(
                        state = ChatPreviewData.privateChat,
                        onEvent = {},
                        onBack = {},
                        recording = recording.copy(meter = previewMeter()),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, VoiceRecordingUiState> =
            mapOf(
                "held" to VoiceRecordingUiState(),
                "locked" to VoiceRecordingUiState(isLocked = true),
                "paused" to VoiceRecordingUiState(isLocked = true, isPaused = true),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
