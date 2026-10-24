package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.requests.JoinRequestsUiState
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the join requests page (`docs/TESTING.md`): the requests in light, dark, AMOLED, high contrast, right to
 * left, a 700 dp window and twice the font size; a channel's, one being answered, "Dismiss all", a page that failed,
 * none, and the error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class JoinRequestsScreenshotTest {
    @Test
    fun requestsLight() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.content)

    @Test
    fun requestsDark() = capture(ScreenshotVariant.Dark, JoinRequestsPreviewData.content)

    @Test
    fun requestsAmoled() = capture(ScreenshotVariant.Amoled, JoinRequestsPreviewData.content)

    @Test
    fun requestsHighContrast() = capture(ScreenshotVariant.HighContrast, JoinRequestsPreviewData.content)

    @Test
    fun requestsRtl() = capture(ScreenshotVariant.Rtl, JoinRequestsPreviewData.content)

    @Test
    @Config(qualifiers = MEDIUM)
    fun requestsMedium() = capture(ScreenshotVariant.Medium, JoinRequestsPreviewData.content)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = 2f)
    fun requestsFontScale200() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.content)

    @Test
    fun channel() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.channel)

    @Test
    fun busy() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.busy)

    @Test
    fun dismissAll() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.dismissAll)

    @Test
    fun pageError() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.pageError)

    @Test
    fun empty() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.empty)

    @Test
    fun error() = capture(ScreenshotVariant.Light, JoinRequestsPreviewData.error)

    private fun capture(
        variant: ScreenshotVariant,
        state: JoinRequestsUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { JoinRequestsScreen(state, {}, {}) } }
}
