package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Notifications page in each of its states, on a phone, on an expanded window and at 200 % font scale, and whole
 * on a tall window as the `foss` flavor shows it (with "Keep connected") and as `gms` does (without).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NotificationSettingsScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.notifications)

    @Test
    @Config(qualifiers = TALL)
    fun fossWholeLight() = capture("fossWholeLight", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    @Config(qualifiers = TALL)
    fun fossWholeDark() = capture("fossWholeDark", ThemeMode.Dark, SettingsPreviewData.notificationsBlocked)

    @Test
    @Config(qualifiers = TALL)
    fun gmsWholeLight() =
        capture(
            "gmsWholeLight",
            ThemeMode.Light,
            SettingsPreviewData.notifications.copy(isKeepConnectedOffered = false),
        )

    @Test
    fun blockedLight() = capture("blockedLight", ThemeMode.Light, SettingsPreviewData.notificationsBlocked)

    @Test
    fun blockedDark() = capture("blockedDark", ThemeMode.Dark, SettingsPreviewData.notificationsBlocked)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.notifications)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.notificationsBlocked)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, NotificationSettingsUiState())

    @Test
    fun errorDark() =
        capture(
            "errorDark",
            ThemeMode.Dark,
            NotificationSettingsUiState(scopes = ScopeSettingsState.Error(DomainError.Network)),
        )

    @Test
    fun resetDialogLight() =
        capture("resetDialogLight", ThemeMode.Light, SettingsPreviewData.notifications.copy(isResetDialogOpen = true))

    @Test
    fun resetDialogDark() =
        capture("resetDialogDark", ThemeMode.Dark, SettingsPreviewData.notifications.copy(isResetDialogOpen = true))

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: NotificationSettingsUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        NotificationSettingsScreen(state = state, onEvent = {}, onOpen = {}, onBack = {})
    }

    private companion object {
        /** Tall enough for the whole page at 100 % font scale. */
        const val TALL = "w411dp-h2000dp-xxhdpi"
    }
}
