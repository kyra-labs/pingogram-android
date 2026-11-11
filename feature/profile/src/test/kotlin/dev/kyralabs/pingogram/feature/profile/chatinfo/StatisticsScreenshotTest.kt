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
 * font size; a supergroup's with its top members, the error and loading; a poll's votes in every variant. Tall, so the
 * charts show.
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

    @Test
    @Config(qualifiers = COMPACT)
    fun pollLight() = capture(ScreenshotVariant.Light, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = COMPACT)
    fun pollDark() = capture(ScreenshotVariant.Dark, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = COMPACT)
    fun pollAmoled() = capture(ScreenshotVariant.Amoled, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = COMPACT)
    fun pollHighContrast() = capture(ScreenshotVariant.HighContrast, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = COMPACT)
    fun pollRtl() = capture(ScreenshotVariant.Rtl, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = MEDIUM)
    fun pollMedium() = capture(ScreenshotVariant.Medium, StatisticsPreviewData.poll, isPoll = true)

    @Test
    @Config(qualifiers = TALL, fontScale = 2f)
    fun pollFontScale200() = capture(ScreenshotVariant.Light, StatisticsPreviewData.poll, isPoll = true)

    private fun capture(
        variant: ScreenshotVariant,
        state: StatisticsUiState,
        isPoll: Boolean = false,
    ) = captureRoboImage { ScreenshotTheme(variant) { StatisticsScreen(state, {}, {}, isPoll = isPoll) } }
}

private const val TALL = "w411dp-h1800dp-xxhdpi"
