package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the media viewer (`docs/TESTING.md`), light and dark on a compact phone and light on an expanded window:
 * a photo with its chrome and caption, a video with its controls, a photo still downloading, the chrome hidden, the
 * error, and the floating toolbar: Share, Save and More; with Delete too, and its confirmation; Share waiting for the
 * download; and More alone in a chat that protects its content. The viewer is dark in either theme, so light and dark
 * differ only where the app's theme shows through (none here). Inspection mode, so Coil shows solid colors for the
 * images ([PreviewImages]) instead of loading TDLib files; the downloading photo stays loading.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MediaViewerScreenshotTest(
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
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    /**
     * At 200% font scale (`REQUIREMENTS.md` §6.3), the states with the most text: the caption and top bar, a video's
     * controls, the error, and the delete question. They grow and never clip.
     */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName in LARGE_FONT_STATES)
        capture(ScreenshotVariant.Light, goldenName = "fontScale200")
    }

    /** The audit's other looks (`docs/DESIGN_SYSTEM.md` §12) for one representative state only. */
    private fun captureAudit(variant: ScreenshotVariant) {
        assumeTrue(stateName == "photo")
        capture(variant)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        goldenName: String = variant.goldenName,
    ) {
        val shot = SHOTS.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ScreenshotTheme(variant) {
                    PreviewImages(loading = shot.imagesLoading) {
                        MediaViewerScreen(state = shot.state, onEvent = {}, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        if (shot.hideChrome) {
            composeRule
                .onNode(hasContentDescription("Photo, 3 of 120", substring = true))
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$goldenName"))
    }

    /** A state to capture, with its images loading or not, and its chrome shown or hidden. */
    class Shot(
        val state: MediaViewerUiState,
        val imagesLoading: Boolean = false,
        val hideChrome: Boolean = false,
    )

    companion object {
        val SHOTS =
            mapOf(
                "photo" to Shot(MediaPreviewData.photo),
                "video" to Shot(MediaPreviewData.video),
                "videoWaitingForTap" to Shot(MediaPreviewData.videoWaitingForTap),
                "downloading" to Shot(MediaPreviewData.downloading, imagesLoading = true),
                "chromeHidden" to Shot(MediaPreviewData.photo, hideChrome = true),
                "error" to Shot(MediaPreviewData.error),
                "actions" to Shot(MediaPreviewData.withActions),
                "allActions" to Shot(MediaPreviewData.allActions),
                "deleteConfirmation" to Shot(MediaPreviewData.deleteConfirmation),
                "sharing" to Shot(MediaPreviewData.sharing, imagesLoading = true),
                "protectedContent" to Shot(MediaPreviewData.protectedContent),
            )

        private const val FONT_SCALE_200 = 2f
        private val LARGE_FONT_STATES = setOf("photo", "video", "error", "deleteConfirmation")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = SHOTS.keys.map { arrayOf(it) }
    }
}
