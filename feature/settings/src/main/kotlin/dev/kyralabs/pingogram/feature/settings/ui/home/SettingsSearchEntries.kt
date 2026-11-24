package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSubPage
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What settings search looks through (`docs/DESIGN_SYSTEM.md` §13.9): every group's entries, Edit profile and the
 * user's QR code, the pages above them ([SubPageEntries]), then single settings found by their own names
 * ([SettingEntries]).
 */
internal val SearchEntries: List<HomeEntry> by lazy {
    HomeGroups.flatMap { it.entries } + AccountEntries + SubPageEntries + SettingEntries
}

/**
 * Settings that search finds by their own names, not only by their page's (owner decision 2026-11-23: every toggle of
 * `REQUIREMENTS.md` §5.10): each with its page's title as its summary (where it is) and its page's icon. A tap opens
 * the page it is on.
 */
internal val SettingEntries: List<HomeEntry> by lazy {
    listOf(
        setting(SettingsPage.PrivacySecurity, R.string.settings_incognito_keyboard),
        setting(SettingsPage.PrivacySecurity, R.string.settings_clean_links),
        setting(SettingsPage.PrivacySecurity, R.string.settings_share_phone_number),
        setting(SettingsPage.ChatFolders, R.string.settings_folder_tab_counters),
        setting(SettingsPage.Chats, R.string.settings_send_silently),
        setting(SettingsPage.Chats, R.string.settings_confirm_bot_commands),
        setting(SettingsPage.Chats, R.string.settings_undo_redo),
        setting(SettingsPage.Chats, R.string.settings_drafts_button),
    )
}

private fun setting(
    page: SettingsPage,
    @StringRes title: Int,
): HomeEntry {
    val parent = parentEntry(page)
    return HomeEntry(HomeDestination.Page(page), title, parent.title, parent.icon)
}

/** Edit profile and the user's QR code, which the account card opens rather than a group. */
private val AccountEntries: List<HomeEntry> =
    listOf(
        page(
            SettingsPage.EditProfile,
            R.string.settings_edit_profile_button,
            R.string.settings_home_edit_profile_summary,
            UiR.drawable.ic_edit_filled,
        ),
        page(
            SettingsPage.MyQrCode,
            R.string.settings_my_qr_title,
            R.string.settings_home_qr_summary,
            UiR.drawable.ic_qr_code,
        ),
    )

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

/** The entry of Settings home, or of the account card, that opens [page]. */
private fun parentEntry(page: SettingsPage): HomeEntry =
    (HomeGroups.flatMap { it.entries } + AccountEntries).first { it.destination == HomeDestination.Page(page) }

@StringRes
private fun subPageTitle(subPage: SettingsSubPage): Int =
    when (subPage) {
        SettingsSubPage.ColorTheme -> R.string.settings_color_theme
        SettingsSubPage.ChatAppearance -> R.string.settings_chat_appearance
        SettingsSubPage.DoubleTapReaction -> R.string.settings_double_tap
        SettingsSubPage.SwipeActions -> R.string.settings_swipe_actions
        SettingsSubPage.ChatsShownFirst -> R.string.shown_first_title
        SettingsSubPage.Snippets -> R.string.settings_snippets
        SettingsSubPage.Transcription -> R.string.settings_transcription
        SettingsSubPage.RaiseToListen -> R.string.settings_raise_to_listen
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
        SettingsSubPage.Passkeys -> R.string.settings_passkeys
        SettingsSubPage.TwoStepVerification -> R.string.settings_two_step_verification
        SettingsSubPage.PasscodeLock -> R.string.settings_passcode_lock
        SettingsSubPage.LockedChats -> R.string.locked_chats_title
        SettingsSubPage.AutoDelete -> R.string.settings_auto_delete
        SettingsSubPage.DeleteAccount -> R.string.settings_delete_account
        SettingsSubPage.LoginEmail -> R.string.settings_login_email
        SettingsSubPage.PersonalChannel -> R.string.settings_personal_channel
        SettingsSubPage.ProfileColor -> R.string.settings_profile_color
    }
