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
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
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
        val shot = SHOTS.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
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
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
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
                "downloading" to Shot(MediaPreviewData.downloading, imagesLoading = true),
                "chromeHidden" to Shot(MediaPreviewData.photo, hideChrome = true),
                "error" to Shot(MediaPreviewData.error),
                "actions" to Shot(MediaPreviewData.withActions),
                "allActions" to Shot(MediaPreviewData.allActions),
                "deleteConfirmation" to Shot(MediaPreviewData.deleteConfirmation),
                "sharing" to Shot(MediaPreviewData.sharing, imagesLoading = true),
                "protectedContent" to Shot(MediaPreviewData.protectedContent),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = SHOTS.keys.map { arrayOf(it) }
    }
}
