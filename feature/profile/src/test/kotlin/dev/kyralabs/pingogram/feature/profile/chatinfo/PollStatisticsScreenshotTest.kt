package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsUiState
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a poll's statistics page (`docs/TESTING.md`): the votes over time and the totals in light, dark, AMOLED,
 * high contrast, right to left, a 700 dp window and twice the font size; without a graph yet, and the error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PollStatisticsScreenshotTest {
    @Test
    fun light() = capture(ScreenshotVariant.Light, PollStatisticsPreviewData.content)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark, PollStatisticsPreviewData.content)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled, PollStatisticsPreviewData.content)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast, PollStatisticsPreviewData.content)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl, PollStatisticsPreviewData.content)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium, PollStatisticsPreviewData.content)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light, PollStatisticsPreviewData.content)

    @Test
    fun noGraph() = capture(ScreenshotVariant.Light, PollStatisticsPreviewData.noGraph)

    @Test
    fun error() = capture(ScreenshotVariant.Light, PollStatisticsPreviewData.error)

    private fun capture(
        variant: ScreenshotVariant,
        state: PollStatisticsUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { PollStatisticsScreen(state, {}, {}) } }
}
