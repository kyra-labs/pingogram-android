package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberSampleMinithumbnail
import dev.kyralabs.pingogram.feature.chat.ui.preview.RichPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of rich messages (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Rich message") in the looks the audit asks
 * for (§12): light, dark, true black, high contrast, right to left, a medium window (700 dp) and 200% font scale. A
 * channel's post with its sections closed as Telegram sends it, opened (a list with a checklist item, a photo, a
 * bordered table and a quote), and with only its start sent, its rest loading. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT_TALL)
class RichMessageScreenshotTest(
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
        composeRule.setContent {
            ScreenshotTheme(variant) {
                val minithumbnail = rememberSampleMinithumbnail()
                val state =
                    when (stateName) {
                        "collapsed" -> RichPreviewData.collapsed(minithumbnail)
                        "expanded" -> RichPreviewData.expanded(minithumbnail)
                        else -> RichPreviewData.partial(minithumbnail)
                    }
                ChatScreen(state = state, onEvent = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = listOf("collapsed", "expanded", "partial").map { arrayOf(it) }
    }
}
