package dev.kyralabs.pingogram.feature.media.storypost

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.media.ui.MEDIUM
import dev.kyralabs.pingogram.feature.media.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.media.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.media.ui.goldenPath
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of composing a story (board 10b, `docs/DESIGN_SYSTEM.md` §6 "New story") on a compact phone, light and
 * dark: to close friends with a bold caption, blocked by the week's limit, posting. To close friends also true black,
 * high contrast, right to left, a 700 dp window and 200% font. Inspection mode, so Coil shows solid colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryComposeScreenshotTest(
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
    fun amoled() = only("closeFriends") { capture(ScreenshotVariant.Amoled) }

    @Test
    fun highContrast() = only("closeFriends") { capture(ScreenshotVariant.HighContrast) }

    @Test
    fun rtl() = only("closeFriends") { capture(ScreenshotVariant.Rtl) }

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = only("closeFriends") { capture(ScreenshotVariant.Medium) }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = only("closeFriends") { capture(ScreenshotVariant.Light, goldenName = "fontScale200") }

    private fun only(
        name: String,
        test: () -> Unit,
    ) {
        assumeTrue(stateName == name)
        test()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        goldenName: String = variant.goldenName,
    ) {
        val compose = STATES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScreenshotTheme(variant) {
                    PreviewImages {
                        StoryComposeScreen(
                            state = StoryComposePreviewData.items,
                            compose = compose,
                            caption = StoryComposePreviewData.caption(),
                            onEvent = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$goldenName"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        internal val STATES =
            mapOf(
                "closeFriends" to StoryComposePreviewData.closeFriends,
                "weeklyLimit" to StoryComposePreviewData.weeklyLimit,
                "posting" to StoryComposePreviewData.posting,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
