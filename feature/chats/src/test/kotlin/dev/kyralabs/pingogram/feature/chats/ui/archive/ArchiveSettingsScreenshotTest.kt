package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import dev.kyralabs.pingogram.feature.chats.ui.preview.ArchiveSettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The archive settings page (`docs/DESIGN_SYSTEM.md` §6 "Archive settings"), light and dark in each state (the
 * settings, archiving new chats not offered, loading, failed), and in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (§12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ArchiveSettingsScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ScreenshotVariant.Light)

    @Test
    fun dark() = capture("dark", ScreenshotVariant.Dark)

    @Test
    fun unavailableLight() =
        capture("unavailableLight", ScreenshotVariant.Light, ArchiveSettingsPreviewData.unavailable)

    @Test
    fun unavailableDark() = capture("unavailableDark", ScreenshotVariant.Dark, ArchiveSettingsPreviewData.unavailable)

    @Test
    fun loadingLight() = capture("loadingLight", ScreenshotVariant.Light, ArchiveSettingsPreviewData.loading)

    @Test
    fun errorLight() = capture("errorLight", ScreenshotVariant.Light, ArchiveSettingsPreviewData.error)

    @Test
    fun errorDark() = capture("errorDark", ScreenshotVariant.Dark, ArchiveSettingsPreviewData.error)

    @Test
    fun amoled() = capture("amoled", ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture("highContrast", ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture("rtl", ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture("medium", ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        variant: ScreenshotVariant,
        state: ArchiveSettingsUiState = ArchiveSettingsPreviewData.content,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) { ArchiveSettingsScreen(state = state, onEvent = {}, onBack = {}) }
        }
        composeRule.waitForIdle()
        // Loading shows its indicator once it takes a moment; the golden shows it then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }

    private companion object {
        const val FONT_SCALE_200 = 2f
    }
}
