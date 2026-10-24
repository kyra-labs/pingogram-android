package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.StatisticsUiState
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the statistics page (`docs/TESTING.md`): a channel's (lines, stacked bars, shares of the whole, and a
 * chart without data) in light, dark, AMOLED, high contrast, right to left, a 700 dp window and twice the
 * font size; a supergroup's with its top members, the error and loading. Tall, so the charts show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL)
class StatisticsScreenshotTest {
    @Test
    fun channelLight() = capture(ScreenshotVariant.Light, StatisticsPreviewData.channel)

    @Test
    fun channelDark() = capture(ScreenshotVariant.Dark, StatisticsPreviewData.channel)

    @Test
    fun channelAmoled() = capture(ScreenshotVariant.Amoled, StatisticsPreviewData.channel)

    @Test
    fun channelHighContrast() = capture(ScreenshotVariant.HighContrast, StatisticsPreviewData.channel)

    @Test
    fun channelRtl() = capture(ScreenshotVariant.Rtl, StatisticsPreviewData.channel)

    @Test
    @Config(qualifiers = MEDIUM)
    fun channelMedium() = capture(ScreenshotVariant.Medium, StatisticsPreviewData.channel)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = 2f)
    fun channelFontScale200() = capture(ScreenshotVariant.Light, StatisticsPreviewData.channel)

    @Test
    fun supergroup() = capture(ScreenshotVariant.Light, StatisticsPreviewData.supergroup)

    @Test
    @Config(qualifiers = COMPACT)
    fun error() = capture(ScreenshotVariant.Light, StatisticsPreviewData.error)

    @Test
    @Config(qualifiers = COMPACT)
    fun loading() = capture(ScreenshotVariant.Light, StatisticsUiState.Loading)

    private fun capture(
        variant: ScreenshotVariant,
        state: StatisticsUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { StatisticsScreen(state, {}, {}) } }
}

private const val TALL = "w411dp-h1800dp-xxhdpi"
