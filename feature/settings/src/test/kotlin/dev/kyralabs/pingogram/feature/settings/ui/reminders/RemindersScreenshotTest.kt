package dev.kyralabs.pingogram.feature.settings.ui.reminders

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Reminders page (the owner's boards 29e, 29f): upcoming and done, light and dark, an overdue one, none, loading,
 * AMOLED black, high contrast, right to left, a 700 dp window and twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RemindersScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun overdueLight() = capture("overdueLight", ThemeMode.Light, RemindersPreviewData.overdue)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, RemindersUiState.Empty)

    @Test
    fun emptyDark() = capture("emptyDark", ThemeMode.Dark, RemindersUiState.Empty)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: RemindersUiState = RemindersPreviewData.content,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { Page(state) }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) { Page(RemindersPreviewData.content) }

    @Composable
    private fun Page(state: RemindersUiState) {
        RemindersScreen(state, onEvent = {}, onBack = {})
    }
}
