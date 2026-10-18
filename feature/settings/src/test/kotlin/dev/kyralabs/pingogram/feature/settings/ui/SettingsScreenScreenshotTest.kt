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

/**
 * The Settings screen: with the account, before TDLib has announced it, on an expanded window, and with the log out
 * confirmation; screen captures, so the dialog's window shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.content)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.content)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.content)

    @Test
    fun noAccountLight() = capture("noAccountLight", ThemeMode.Light, SettingsUiState())

    @Test
    fun loggingOutLight() =
        capture("loggingOutLight", ThemeMode.Light, SettingsPreviewData.content.copy(isLoggingOut = true))

    @Test
    fun logOutDialogLight() =
        capture("logOutDialogLight", ThemeMode.Light, SettingsPreviewData.content.copy(isLogOutDialogOpen = true))

    @Test
    fun logOutDialogDark() =
        capture("logOutDialogDark", ThemeMode.Dark, SettingsPreviewData.content.copy(isLogOutDialogOpen = true))

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.content)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: SettingsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        SettingsScreen(state = state, onEvent = {}, onOpenPage = {})
    }
}
