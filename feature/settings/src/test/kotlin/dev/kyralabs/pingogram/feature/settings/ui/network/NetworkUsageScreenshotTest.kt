package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.settings.ui.MEDIUM
import dev.kyralabs.pingogram.feature.settings.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.settings.ui.captureScreen
import dev.kyralabs.pingogram.feature.settings.ui.preview.NetworkUsagePreviewStates
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Network usage in each of its states, light and dark; Wi-Fi's usage in AMOLED black, high contrast, right to left, a
 * 700 dp window and at twice the font size (`docs/DESIGN_SYSTEM.md` §12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NetworkUsageScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wifiLight() = capture("wifiLight", ThemeMode.Light, NetworkUsagePreviewStates.wifi)

    @Test
    fun wifiDark() = capture("wifiDark", ThemeMode.Dark, NetworkUsagePreviewStates.wifi)

    @Test
    fun emptyLight() = capture("emptyLight", ThemeMode.Light, NetworkUsagePreviewStates.roaming)

    @Test
    fun resetDialogLight() = capture("resetDialogLight", ThemeMode.Light, NetworkUsagePreviewStates.resetDialog)

    @Test
    fun resetDialogDark() = capture("resetDialogDark", ThemeMode.Dark, NetworkUsagePreviewStates.resetDialog)

    @Test
    fun resettingLight() = capture("resettingLight", ThemeMode.Light, NetworkUsagePreviewStates.resetting)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, NetworkUsagePreviewStates.error)

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
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, NetworkUsagePreviewStates.wifi)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NetworkUsageUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NetworkUsageScreen(state, onEvent = {}, onBack = {})
    }

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            NetworkUsageScreen(NetworkUsagePreviewStates.wifi, onEvent = {}, onBack = {})
        }
}
