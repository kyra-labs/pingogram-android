package dev.kyralabs.pingogram.feature.settings.navigation

import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.navigation.AutoDeleteRoute
import dev.kyralabs.pingogram.core.navigation.AutoDownloadRoute
import dev.kyralabs.pingogram.core.navigation.BlockedUsersRoute
import dev.kyralabs.pingogram.core.navigation.ChatAppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatsShownFirstRoute
import dev.kyralabs.pingogram.core.navigation.ColorThemeRoute
import dev.kyralabs.pingogram.core.navigation.ConnectedWebsitesRoute
import dev.kyralabs.pingogram.core.navigation.DeleteAccountRoute
import dev.kyralabs.pingogram.core.navigation.LockedChatsRoute
import dev.kyralabs.pingogram.core.navigation.NetworkUsageRoute
import dev.kyralabs.pingogram.core.navigation.NewChatPrivacyRoute
import dev.kyralabs.pingogram.core.navigation.OpenSourceLicensesRoute
import dev.kyralabs.pingogram.core.navigation.PasscodeLockRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySettingRoute
import dev.kyralabs.pingogram.core.navigation.ProxiesRoute
import dev.kyralabs.pingogram.core.navigation.QuickReactionRoute
import dev.kyralabs.pingogram.core.navigation.ReactionNotificationsRoute
import dev.kyralabs.pingogram.core.navigation.StickerSetListRoute
import dev.kyralabs.pingogram.core.navigation.StickersRoute
import dev.kyralabs.pingogram.core.navigation.SwipeActionsRoute
import dev.kyralabs.pingogram.core.navigation.TwoStepVerificationRoute
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSubPage

/** The route of the page settings search opens above its parent page, as that parent opens it. */
internal val SettingsSubPage.route: Any
    get() =
        when (this) {
            SettingsSubPage.ColorTheme -> ColorThemeRoute
            SettingsSubPage.ChatAppearance -> ChatAppearanceRoute
            SettingsSubPage.DoubleTapReaction -> QuickReactionRoute
            SettingsSubPage.SwipeActions -> SwipeActionsRoute
            SettingsSubPage.ChatsShownFirst -> ChatsShownFirstRoute
            SettingsSubPage.TrendingStickers -> StickerSetListRoute(archived = false, emoji = false)
            SettingsSubPage.ArchivedStickers -> StickerSetListRoute(archived = true, emoji = false)
            SettingsSubPage.CustomEmoji -> StickersRoute(emoji = true)
            SettingsSubPage.ReactionNotifications -> ReactionNotificationsRoute
            SettingsSubPage.AutoDownloadMobile -> AutoDownloadRoute(AutoDownloadNetwork.Mobile.name)
            SettingsSubPage.AutoDownloadWiFi -> AutoDownloadRoute(AutoDownloadNetwork.WiFi.name)
            SettingsSubPage.AutoDownloadRoaming -> AutoDownloadRoute(AutoDownloadNetwork.Roaming.name)
            SettingsSubPage.Proxies -> ProxiesRoute
            SettingsSubPage.NetworkUsage -> NetworkUsageRoute
            SettingsSubPage.Licenses -> OpenSourceLicensesRoute
            SettingsSubPage.LastSeen -> privacy(PrivacySetting.LastSeen)
            SettingsSubPage.PhoneNumberPrivacy -> privacy(PrivacySetting.PhoneNumber)
            SettingsSubPage.ProfilePhotoPrivacy -> privacy(PrivacySetting.ProfilePhoto)
            SettingsSubPage.BioPrivacy -> privacy(PrivacySetting.Bio)
            SettingsSubPage.BirthdayPrivacy -> privacy(PrivacySetting.Birthdate)
            SettingsSubPage.ForwardedMessagesPrivacy -> privacy(PrivacySetting.ForwardedMessages)
            SettingsSubPage.CallsPrivacy -> privacy(PrivacySetting.Calls)
            SettingsSubPage.InvitationsPrivacy -> privacy(PrivacySetting.ChatInvites)
            SettingsSubPage.VoiceMessagesPrivacy -> privacy(PrivacySetting.VoiceMessages)
            SettingsSubPage.NewChats -> NewChatPrivacyRoute
            SettingsSubPage.BlockedUsers -> BlockedUsersRoute
            SettingsSubPage.Websites -> ConnectedWebsitesRoute
            SettingsSubPage.TwoStepVerification -> TwoStepVerificationRoute
            SettingsSubPage.PasscodeLock -> PasscodeLockRoute
            SettingsSubPage.LockedChats -> LockedChatsRoute
            SettingsSubPage.AutoDelete -> AutoDeleteRoute
            SettingsSubPage.DeleteAccount -> DeleteAccountRoute
        }

private fun privacy(setting: PrivacySetting) = PrivacySettingRoute(setting.name)
