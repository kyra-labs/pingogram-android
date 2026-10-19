package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the message actions on a compact phone, light and dark (`docs/TESTING.md`): the actions sheet of the
 * user's own message and of someone else's, the delete confirmation, and the composer replying and editing; the own
 * message's sheet also on an expanded window, and the reply bar at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MessageActionsScreenshotTest(
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

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        // One state is enough for the wide layout: the sheet keeps its maximum width.
        assumeTrue(stateName == "ownSheet")
        capture("expanded", ThemeMode.Light)
    }

    /** At 200% font scale the bar grows instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "replyBar")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val (state, text) = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = state, onEvent = {}, onBack = {}, composerText = ComposerText(text))
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, Pair<ChatUiState, FormattedText>> =
            mapOf(
                "ownSheet" to (ChatPreviewData.ownActionsSheet to FormattedText.Empty),
                "incomingSheet" to (ChatPreviewData.incomingActionsSheet to FormattedText.Empty),
                "deleteDialog" to (ChatPreviewData.deleteConfirmation to FormattedText.Empty),
                "replyBar" to (ChatPreviewData.replying to FormattedText.Empty),
                "editBar" to
                    (ChatPreviewData.editing to FormattedText(ChatPreviewData.EDITED_TEXT, persistentListOf())),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
