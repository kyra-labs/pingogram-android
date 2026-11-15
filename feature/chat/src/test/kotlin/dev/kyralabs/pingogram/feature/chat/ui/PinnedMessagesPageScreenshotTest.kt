package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedMessagesScreen
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedPagePreviewStates
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedPageUiState
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the pinned messages page (the owner's design "Pinned messages page", `docs/DESIGN_SYSTEM.md` §6 "Pinned
 * messages bar") on a compact phone, light and dark, and on an expanded window: the pins of a beta group (a formatted
 * text, a file, a link preview), loading, none, none found and an error. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PinnedMessagesPageScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() {
        assumeTrue(stateName == "content")
        capture("dark", ThemeMode.Dark)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        assumeTrue(stateName == "content")
        capture("expanded", ThemeMode.Light)
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3): the cards grow and never clip. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "content")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                PinnedMessagesScreen(state = state, onEvent = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private val NAMES = listOf("content", "loading", "empty", "noneFound", "error")

        val STATES: Map<String, PinnedPageUiState> = NAMES.zip(PinnedPagePreviewStates().values.toList()).toMap()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
