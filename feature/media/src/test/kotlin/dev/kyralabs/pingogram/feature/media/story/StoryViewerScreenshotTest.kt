package dev.kyralabs.pingogram.feature.media.story

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import dev.kyralabs.pingogram.feature.media.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.media.ui.EXPANDED
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
 * Goldens of the stories viewer (`docs/DESIGN_SYSTEM.md` §6, Story viewer) on a compact phone, light and dark, and on
 * an expanded window: a user's photo story with its caption, the reply field and the reaction button; a channel's video
 * with the mute button and reactions only; the row of reactions open with one chosen; content Pingogram cannot show;
 * a story that failed to load. Right to left for the photo. The viewer is dark in either theme. Inspection mode, so
 * Coil shows solid colors for the images ([PreviewImages]).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryViewerScreenshotTest(
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
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture(ScreenshotVariant.Light, goldenName = "expanded")

    @Test
    fun rtl() {
        assumeTrue(stateName == "photo")
        capture(ScreenshotVariant.Rtl)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        goldenName: String = variant.goldenName,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScreenshotTheme(variant) {
                    PreviewImages {
                        StoryViewerScreen(state = state, onEvent = {}, onClose = {}, replyText = TextFieldState())
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$goldenName"))
    }

    companion object {
        val STATES =
            mapOf(
                "photo" to StoryViewerPreviewData.photo,
                "video" to StoryViewerPreviewData.video,
                "reacting" to StoryViewerPreviewData.reacting,
                "unsupported" to StoryViewerPreviewData.unsupported,
                "failed" to StoryViewerPreviewData.failed,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
