package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.activity.ComponentActivity
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
 * Cleanup suggestions' page (FR-X24), light and dark, in AMOLED black, high contrast, right to left, a 700 dp window
 * and at twice the font size (`docs/DESIGN_SYSTEM.md` §12): nothing to suggest, loading, off, a group to leave, a chat
 * never opened, and Leave's confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CleanupScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = page("light", ThemeMode.Light)

    @Test
    fun dark() = page("dark", ThemeMode.Dark)

    @Test
    fun loadingLight() = page("loadingLight", ThemeMode.Light, CleanupPreviewStates.loading)

    @Test
    fun offLight() = page("offLight", ThemeMode.Light, CleanupPreviewStates.off)

    @Test
    fun suggestionLight() = page("suggestionLight", ThemeMode.Light, CleanupPreviewStates.group)

    @Test
    fun suggestionDark() = page("suggestionDark", ThemeMode.Dark, CleanupPreviewStates.group)

    @Test
    fun neverOpenedLight() = page("neverOpenedLight", ThemeMode.Light, CleanupPreviewStates.neverOpened)

    @Test
    fun leaveLight() = page("leaveLight", ThemeMode.Light, CleanupPreviewStates.confirmingLeave)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun suggestionFontScale200() = page("suggestionFontScale200", ThemeMode.Light, CleanupPreviewStates.group)

    @Test
    @Config(qualifiers = MEDIUM)
    fun suggestionMedium() =
        composeRule.captureScreen(javaClass, "suggestionMedium", ScreenshotVariant.Medium) {
            CleanupScreen(CleanupPreviewStates.group, onEvent = {}, onBack = {})
        }

    @Test
    fun amoled() = audit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = audit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = audit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = audit(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun fontScale200() = page("fontScale200", ThemeMode.Light)

    private fun page(
        name: String,
        themeMode: ThemeMode,
        state: CleanupUiState = CleanupPreviewStates.none,
    ) = composeRule.captureScreen(javaClass, name, themeMode) { CleanupScreen(state, onEvent = {}, onBack = {}) }

    private fun audit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            CleanupScreen(CleanupPreviewStates.none, onEvent = {}, onBack = {})
        }
}
