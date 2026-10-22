package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesSheet
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.PinnedPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the pinned messages bar on a compact phone, light and dark, and on an expanded window (`docs/TESTING.md`,
 * `REQUIREMENTS.md` FR-C9): one pinned message the user may unpin in a private chat, the second newest of five in a
 * group ("4 of 5", its segment second from the bottom), which the user may only hide, and the sheet of every pinned
 * message, oldest first. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PinnedMessagesScreenshotTest(
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
    fun expanded() = capture("expanded", ThemeMode.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val (state, sheet) = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                ChatScreen(state = state, onEvent = {}, onBack = {})
                sheet?.let { PinnedMessagesSheet(sheet = it, onEvent = {}) }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        val STATES: Map<String, Pair<ChatUiState, PinnedSheet?>> =
            with(PinnedPreviewData) {
                mapOf(
                    "one" to (privateWithPin to null),
                    "many" to (groupWithPins to null),
                    "sheet" to (groupWithPins to sheet),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
