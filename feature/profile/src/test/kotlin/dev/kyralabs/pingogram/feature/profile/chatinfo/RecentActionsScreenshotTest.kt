package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsUiState
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the recent actions page (`docs/TESTING.md`): the actions in light, dark, AMOLED, high contrast, right to
 * left, a 700 dp window and twice the font size; filtered while loading, an older page that failed, none, the error
 * and loading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RecentActionsScreenshotTest {
    @Test
    fun actionsLight() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.content)

    @Test
    fun actionsDark() = capture(ScreenshotVariant.Dark, RecentActionsPreviewData.content)

    @Test
    fun actionsAmoled() = capture(ScreenshotVariant.Amoled, RecentActionsPreviewData.content)

    @Test
    fun actionsHighContrast() = capture(ScreenshotVariant.HighContrast, RecentActionsPreviewData.content)

    @Test
    fun actionsRtl() = capture(ScreenshotVariant.Rtl, RecentActionsPreviewData.content)

    @Test
    @Config(qualifiers = MEDIUM)
    fun actionsMedium() = capture(ScreenshotVariant.Medium, RecentActionsPreviewData.content)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = 2f)
    fun actionsFontScale200() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.content)

    @Test
    fun filtering() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.filtering)

    @Test
    fun pageError() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.pageError)

    @Test
    fun empty() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.empty)

    @Test
    fun error() = capture(ScreenshotVariant.Light, RecentActionsPreviewData.error)

    @Test
    fun loading() = capture(ScreenshotVariant.Light, RecentActionsUiState.Loading)

    private fun capture(
        variant: ScreenshotVariant,
        state: RecentActionsUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { RecentActionsScreen(state, {}, {}) } }
}
