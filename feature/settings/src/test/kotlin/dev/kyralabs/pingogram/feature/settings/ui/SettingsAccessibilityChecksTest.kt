package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.account.DeleteAccountScreen
import dev.kyralabs.pingogram.feature.settings.ui.account.autodelete.AutoDeleteScreen
import dev.kyralabs.pingogram.feature.settings.ui.account.email.LoginEmailScreen
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberScreen
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFolderEditScreen
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFoldersScreen
import dev.kyralabs.pingogram.feature.settings.ui.folders.invite.FolderInviteContent
import dev.kyralabs.pingogram.feature.settings.ui.preview.AccountPagePreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChangePhoneNumberPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFolderEditPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.ChatFoldersPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.DeleteAccountPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.FolderInvitePreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.PrivacyPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickerSetSheetPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.StickersPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.privacy.messages.NewChatPrivacyScreen
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickerSetListScreen
import dev.kyralabs.pingogram.feature.settings.ui.stickers.StickersScreen
import dev.kyralabs.pingogram.feature.settings.ui.stickers.set.StickerSetContent
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
    fun changePhoneNumber() =
        check { ChangePhoneNumberScreen(state = ChangePhoneNumberPreviewStates.number, onEvent = {}, onBack = {}) }

    @Test
    fun changePhoneNumberCode() =
        check { ChangePhoneNumberScreen(state = ChangePhoneNumberPreviewStates.code, onEvent = {}, onBack = {}) }

    @Test
    fun loginEmailAddress() =
        check { LoginEmailScreen(state = AccountPagePreviewStates.loginEmailAddress, onEvent = {}, onBack = {}) }

    @Test
    fun loginEmailCode() =
        check { LoginEmailScreen(state = AccountPagePreviewStates.loginEmailCode, onEvent = {}, onBack = {}) }

    @Test
    fun autoDelete() =
        check { AutoDeleteScreen(state = AccountPagePreviewStates.autoDelete, onEvent = {}, onBack = {}) }

    @Test
    fun newChatPrivacy() =
        check { NewChatPrivacyScreen(state = PrivacyPreviewStates.newChatsWithoutPremium, onEvent = {}, onBack = {}) }

    @Test
    fun deleteAccount() =
        check { DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {}) }

    @Test
    fun deleteAccountConfirmation() =
        check { DeleteAccountScreen(state = DeleteAccountPreviewStates.confirming, onEvent = {}, onBack = {}) }

    @Test
    fun chatFolders() =
        check {
            ChatFoldersScreen(state = ChatFoldersPreviewStates.content, onEvent = {}, onOpenFolder = {}, onBack = {})
        }

    @Test
    fun chatFolderEdit() =
        check {
            ChatFolderEditScreen(state = ChatFolderEditPreviewStates.editing, picker = null, onEvent = {}, onBack = {})
        }

    @Test
    fun chatFolderPicker() =
        check {
            ChatFolderEditScreen(
                state = ChatFolderEditPreviewStates.editing,
                picker = ChatFolderEditPreviewStates.picker,
                onEvent = {},
                onBack = {},
            )
        }

    @Test
    fun folderInvite() =
        check { FolderInviteContent(state = FolderInvitePreviewStates.newFolder, onEvent = {}, onCancel = {}) }

    @Test
    fun folderInviteFailed() =
        check { FolderInviteContent(state = FolderInvitePreviewStates.offline, onEvent = {}, onCancel = {}) }

    @Test
    fun stickers() =
        check {
            StickersScreen(StickersPreviewStates.content, onEvent = {}, onOpenList = {}, onShare = {}, onBack = {})
        }

    @Test
    fun trendingStickers() =
        check {
            StickerSetListScreen(archived = false, state = StickersPreviewStates.trending, onEvent = {}, onBack = {})
        }

    @Test
    fun stickerSet() =
        check {
            StickerSetContent(StickerSetSheetPreviewStates.notAdded, onEvent = {}, onShare = {}, onClose = {})
        }

    @Test
    fun stickerSetNotFound() =
        check {
            StickerSetContent(StickerSetSheetPreviewStates.notFound, onEvent = {}, onShare = {}, onClose = {})
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
