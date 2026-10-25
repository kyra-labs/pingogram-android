package dev.kyralabs.pingogram.core.domain.model

/**
 * When Telegram moves chats into the archive and keeps them there (td_api.tl `archiveChatListSettings`), the account's
 * settings, the same on every device. Without them, a new message brings an archived chat back to the main list
 * unless it is muted.
 *
 * @property archiveAndMuteNewChatsFromNonContacts new chats from people who are not contacts go to the archive,
 *   muted. Telegram lets the user turn it on only where
 *   [ArchiveSettingsAvailability.canArchiveAndMuteNewChats] says so.
 * @property keepUnmutedChatsArchived archived chats that are not muted stay in the archive when a new message arrives.
 * @property keepChatsFromFoldersArchived the same for archived chats a folder includes by name or pins; implied while
 *   [keepUnmutedChatsArchived] is on, which then keeps every unmuted chat.
 */
data class ArchiveSettings(
    val archiveAndMuteNewChatsFromNonContacts: Boolean = false,
    val keepUnmutedChatsArchived: Boolean = false,
    val keepChatsFromFoldersArchived: Boolean = false,
)

/**
 * The [settings] as Telegram has them, and whether Telegram lets the account turn on archiving and muting new chats
 * from non-contacts ([canArchiveAndMuteNewChats], TDLib's option `can_archive_and_mute_new_chats_from_unknown_users`).
 */
data class ArchiveSettingsAvailability(
    val settings: ArchiveSettings,
    val canArchiveAndMuteNewChats: Boolean,
)

/**
 * How the "Archived chats" row shows at the top of the main chat list, kept on this device: with the names of the
 * first archived chats ([Full], the default), as one line ([Compact]), or not at all ([Hidden]), the archive then
 * reached from the chat list's top bar.
 */
enum class ArchiveRowStyle {
    Full,
    Compact,
    Hidden,
}
