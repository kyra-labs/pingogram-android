package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.chat.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a channel's direct messages as its administrators see them (the owner's board 7b, `docs/DESIGN_SYSTEM.md`
 * §6 "Channel direct messages"): who wrote, nobody yet, nobody under a filter, and a failure. Each light, dark, with
 * AMOLED black, in high contrast, right to left, on a 700 dp window and at 200% font scale. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DirectMessagesScreenshotTest(
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
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(
                variant,
            ) { DirectMessagesScreen(state = state, onEvent = {}, onOpenTopic = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        val STATES =
            with(DirectMessagesPreviewData) {
                mapOf("rows" to rows, "empty" to empty, "filteredEmpty" to filteredEmpty, "failed" to failed)
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
