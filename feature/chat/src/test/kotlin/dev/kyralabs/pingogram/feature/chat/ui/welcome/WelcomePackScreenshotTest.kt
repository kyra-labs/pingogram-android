package dev.kyralabs.pingogram.feature.chat.ui.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the welcome pack at the top of a group's conversation (the owner's board 6, `docs/TESTING.md`) on a
 * compact phone: in full and collapsed; light, dark, AMOLED, high contrast and right to left, and in full at 200% font.
 * Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WelcomePackScreenshotTest(
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
    fun amoled() {
        assumeTrue(stateName == "full")
        capture(ScreenshotVariant.Amoled)
    }

    @Test
    fun highContrast() {
        assumeTrue(stateName == "full")
        capture(ScreenshotVariant.HighContrast)
    }

    @Test
    fun rtl() {
        assumeTrue(stateName == "full")
        capture(ScreenshotVariant.Rtl)
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3): the pack grows and scrolls rather than clip. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "full")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val pack = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatScreen(state = ChatPreviewData.groupChat, onEvent = {}, onBack = {}, welcome = pack)
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, WelcomePackUi> =
            mapOf(
                "full" to welcomePackPreview,
                "collapsed" to welcomePackPreview.copy(collapsed = true),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
