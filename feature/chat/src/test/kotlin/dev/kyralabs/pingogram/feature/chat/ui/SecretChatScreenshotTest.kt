package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.preview.SecretChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatSheet
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of secret chats on a compact phone, light and dark (`docs/TESTING.md`, `REQUIREMENTS.md` FR-E1): a ready
 * chat with its lock, timer badge and key in the top bar, the banner above its first message and the timer in the
 * bubbles; one waiting for the other user, with the banner in place of the empty state; one that ended; the
 * self-destruct timer sheet, also while a choice goes to TDLib; the encryption key sheet, also before the key is
 * agreed, and the confirmation before ending the chat. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SecretChatScreenshotTest(
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
        assumeTrue(stateName == "ready" || stateName == "key")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val (state, sheet) = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = state, onEvent = {}, onBack = {})
                sheet?.let { SecretChatBottomSheet(sheet = it, onEvent = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        val STATES: Map<String, Pair<ChatUiState, SecretChatSheet?>> =
            with(SecretChatPreviewData) {
                mapOf(
                    "ready" to (ready to null),
                    "pending" to (pending to null),
                    "closed" to (closed to null),
                    "timer" to (ready to timer),
                    "timerChanging" to (ready to timerChanging),
                    "key" to (ready to key),
                    "keyPending" to (pending to keyPending),
                    "endConfirmation" to (ready to key.copy(confirmingEnd = true)),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
