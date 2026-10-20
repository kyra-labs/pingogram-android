package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automatic download page of a network: Wi-Fi's defaults, mobile data under Data Saver, roaming with nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AutoDownloadScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.autoDownloadWiFi)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.autoDownloadWiFi)

    @Test
    fun dataSaverLight() = capture("dataSaverLight", ThemeMode.Light, SettingsPreviewData.autoDownloadMobileDataSaver)

    @Test
    fun dataSaverDark() = capture("dataSaverDark", ThemeMode.Dark, SettingsPreviewData.autoDownloadMobileDataSaver)

    @Test
    fun nothingLight() =
        capture(
            "nothingLight",
            ThemeMode.Light,
            AutoDownloadUiState(AutoDownloadNetwork.Roaming, rules = AutoDownloadRules.Nothing),
        )

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.autoDownloadWiFi)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: AutoDownloadUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        AutoDownloadScreen(state = state, onEvent = {}, onBack = {})
    }
}
