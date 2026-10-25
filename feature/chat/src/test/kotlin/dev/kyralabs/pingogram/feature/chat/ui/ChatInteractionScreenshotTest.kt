package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the conversation as the user interacts with it (`docs/TESTING.md`), light and dark on a compact phone: a
 * voice message playing, its progress drawn from the player's position; and the message a reply quote was tapped for,
 * highlighted at full strength. Brand colors, so goldens do not depend on a wallpaper; inspection mode, so Coil shows
 * solid colors for the images ([PreviewImages]).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatInteractionScreenshotTest(
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

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        // The highlighted reply: the voice message of the other state is far above the bottom at 200%.
        assumeTrue(stateName == "replyHighlighted")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val sample = SAMPLES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    PreviewImages {
                        ChatScreen(
                            state = sample.state,
                            onEvent = {},
                            onBack = {},
                            jumpState = remember { MessageJumpState(sample.highlightedId) },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    /** A conversation [state], with message [highlightedId] highlighted if not null. */
    class Sample(
        val state: ChatUiState,
        val highlightedId: Long? = null,
    )

    companion object {
        val SAMPLES =
            mapOf(
                "voicePlaying" to Sample(ChatPreviewData.voicePlaying),
                // Linus's message, which the newest one replies to.
                "replyHighlighted" to Sample(ChatPreviewData.groupChat, highlightedId = 25),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun samples(): List<Array<Any>> = SAMPLES.keys.map { arrayOf(it) }
    }
}
