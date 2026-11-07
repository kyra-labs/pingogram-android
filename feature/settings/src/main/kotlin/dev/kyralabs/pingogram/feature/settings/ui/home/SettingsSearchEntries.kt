package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSubPage
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What settings search looks through (`docs/DESIGN_SYSTEM.md` §13.9): every group's entries, Edit profile and the
 * user's QR code, then the pages above them ([SubPageEntries]).
 */
internal val SearchEntries: List<HomeEntry> by lazy {
    HomeGroups.flatMap { it.entries } +
        listOf(
            page(
                SettingsPage.EditProfile,
                R.string.settings_edit_profile_button,
                R.string.settings_home_edit_profile_summary,
                R.drawable.ic_edit,
            ),
            page(
                SettingsPage.MyQrCode,
                R.string.settings_my_qr_title,
                R.string.settings_home_qr_summary,
                UiR.drawable.ic_qr_code,
            ),
        ) +
        SubPageEntries
}

/**
 * The pages above Settings home's pages that search finds, one per [SettingsSubPage]: each with its own title, its
 * parent page's title as its summary (where it is) and its parent's icon, so a word of the parent ("privacy") finds
 * them too. A tap opens the page with its parent below it.
 */
internal val SubPageEntries: List<HomeEntry> by lazy {
    SettingsSubPage.entries.map { subPage ->
        val parent = parentEntry(subPage.parent)
        HomeEntry(HomeDestination.SubPage(subPage), subPageTitle(subPage), parent.title, parent.icon)
    }
}

/** The entry of Settings home that opens [page]. */
private fun parentEntry(page: SettingsPage): HomeEntry =
    HomeGroups.flatMap { it.entries }.first { it.destination == HomeDestination.Page(page) }

@StringRes
private fun subPageTitle(subPage: SettingsSubPage): Int =
    when (subPage) {
        SettingsSubPage.ColorTheme -> R.string.settings_color_theme
        SettingsSubPage.ChatAppearance -> R.string.settings_chat_appearance
        SettingsSubPage.DoubleTapReaction -> R.string.settings_double_tap
        SettingsSubPage.SwipeActions -> R.string.settings_swipe_actions
        SettingsSubPage.ChatsShownFirst -> R.string.shown_first_title
        SettingsSubPage.TrendingStickers -> R.string.settings_stickers_trending
        SettingsSubPage.ArchivedStickers -> R.string.settings_stickers_archived
        SettingsSubPage.CustomEmoji -> R.string.settings_emoji
        SettingsSubPage.ReactionNotifications -> R.string.settings_reactions
        SettingsSubPage.AutoDownloadMobile -> R.string.settings_home_auto_download_mobile
        SettingsSubPage.AutoDownloadWiFi -> R.string.settings_home_auto_download_wifi
        SettingsSubPage.AutoDownloadRoaming -> R.string.settings_home_auto_download_roaming
        SettingsSubPage.Proxies -> R.string.settings_proxy
        SettingsSubPage.NetworkUsage -> R.string.settings_network_usage
        SettingsSubPage.Licenses -> R.string.settings_about_licenses
        SettingsSubPage.LastSeen -> R.string.settings_privacy_last_seen
        SettingsSubPage.PhoneNumberPrivacy -> R.string.settings_privacy_phone_number
        SettingsSubPage.ProfilePhotoPrivacy -> R.string.settings_privacy_profile_photo
        SettingsSubPage.BioPrivacy -> R.string.settings_privacy_bio
        SettingsSubPage.BirthdayPrivacy -> R.string.settings_privacy_birthdate
        SettingsSubPage.ForwardedMessagesPrivacy -> R.string.settings_privacy_forwarded_messages
        SettingsSubPage.CallsPrivacy -> R.string.settings_privacy_calls
        SettingsSubPage.InvitationsPrivacy -> R.string.settings_privacy_chat_invites
        SettingsSubPage.VoiceMessagesPrivacy -> R.string.settings_privacy_voice_messages
        SettingsSubPage.NewChats -> R.string.settings_privacy_new_chats_question
        SettingsSubPage.BlockedUsers -> R.string.settings_blocked_users
        SettingsSubPage.Websites -> R.string.settings_websites
        SettingsSubPage.TwoStepVerification -> R.string.settings_two_step_verification
        SettingsSubPage.PasscodeLock -> R.string.settings_passcode_lock
        SettingsSubPage.LockedChats -> R.string.locked_chats_title
        SettingsSubPage.AutoDelete -> R.string.settings_auto_delete
        SettingsSubPage.DeleteAccount -> R.string.settings_delete_account
    }
