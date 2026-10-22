package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every settings page's controls have a name and a 48 dp touch target ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone, where targets are tightest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsAccessibilityChecksTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun settingsHome() = check { SettingsScreen(state = SettingsPreviewData.content, onEvent = {}, onOpenPage = {}) }

    @Test
    fun appearance() = check { AppearanceScreen(AppearanceUiState.Content(AppearanceSettings()), {}, {}) }

    @Test
    fun chatAppearance() =
        check {
            ChatAppearanceScreen(
                state = ChatAppearanceUiState.Content(ChatAppearance()),
                onTextScale = {},
                onBubbleCorners = {},
                onWallpaper = {},
                onBack = {},
            )
        }

    @Test
    fun colorTheme() =
        check { ColorThemeScreen(ColorThemeUiState.Content(AppearanceSettings(dynamicColor = false)), {}, {}) }

    @Test
    fun privacySecurity() =
        check {
            PrivacySecurityScreen(
                state = SettingsPreviewData.privacySecurity,
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    @Test
    fun notifications() =
        check {
            NotificationSettingsScreen(
                state = SettingsPreviewData.notifications,
                onEvent = {},
                onOpen = {},
                onBack = {},
            )
        }

    @Test
    fun dataStorage() =
        check { DataStorageScreen(SettingsPreviewData.storage, onEvent = {}, onOpenAutoDownload = {}, onBack = {}) }

    @Test
    fun autoDownload() = check { AutoDownloadScreen(SettingsPreviewData.autoDownloadWiFi, onEvent = {}, onBack = {}) }

    @Test
    fun activeSessions() = check { ActiveSessionsScreen(SettingsPreviewData.sessions, onEvent = {}, onBack = {}) }

    @Test
    fun blockedUsers() = check { BlockedUsersScreen(SettingsPreviewData.blockedUsers, onEvent = {}, onBack = {}) }

    @Test
    fun editProfile() = check { EditProfileScreen(SettingsPreviewData.editProfile, onEvent = {}, onPickPhoto = {}) }

    @Test
    fun about() =
        check {
            AboutScreen(
                state = AboutUiState(versionName = "0.3.0"),
                onEvent = {},
                onOpenLicenses = {},
                onBack = {},
                onOpenLinkSettings = {},
            )
        }

    @Test
    fun openSourceLicenses() =
        check {
            OpenSourceLicensesScreen(
                SettingsPreviewData.openSourceLicenses,
                onEvent = {},
                onOpenLibrary = {},
                onBack = {},
            )
        }

    private fun check(content: @Composable () -> Unit) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.assertAccessible()
    }
}
