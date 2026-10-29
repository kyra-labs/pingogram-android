package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The power saving page: the defaults, always on with autoplay left on, and the audit's variants. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PowerSavingScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.powerSaving)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.powerSaving)

    @Test
    fun alwaysLight() = capture("alwaysLight", ThemeMode.Light, SettingsPreviewData.powerSavingOn)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.powerSaving)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            PowerSavingScreen(SettingsPreviewData.powerSaving, onEvent = {}, onBack = {})
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: PowerSavingUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        PowerSavingScreen(state, onEvent = {}, onBack = {})
    }
}
