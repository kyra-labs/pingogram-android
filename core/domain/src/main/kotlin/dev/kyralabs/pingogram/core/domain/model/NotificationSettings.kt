package dev.kyralabs.pingogram.core.domain.model

/**
 * The kinds of chats with default notification settings of their own (td_api.tl `NotificationSettingsScope`): a chat
 * without a setting of its own follows the default of its scope. Secret chats belong to [PrivateChats].
 */
enum class NotificationScope {
    PrivateChats,
    GroupChats,
    ChannelChats,
}

/** The scope whose defaults a chat of [this] type follows. */
val ChatType.notificationScope: NotificationScope
    get() =
        when (this) {
            is ChatType.Private, is ChatType.Secret -> NotificationScope.PrivateChats
            is ChatType.BasicGroup -> NotificationScope.GroupChats
            is ChatType.Supergroup -> if (isChannel) NotificationScope.ChannelChats else NotificationScope.GroupChats
        }

/**
 * The default notification settings of a [NotificationScope] (td_api.tl `scopeNotificationSettings`), as far as
 * Pingogram changes them. Sounds are not here: Android sets them per notification channel.
 *
 * @property isMuted new messages of the scope's chats notify no one, unless a chat says otherwise; TDLib reports a
 *   new setting when a mute for a limited time ends, so this is the state now.
 * @property showPreview notifications show the text of the messages, not only "New message".
 * @property pinnedMessages a message pinned in a chat notifies (`disable_pinned_message_notifications` off).
 * @property mentions messages that mention or reply to the user notify even in muted chats
 *   (`disable_mention_notifications` off).
 */
data class ScopeNotificationSettings(
    val isMuted: Boolean,
    val showPreview: Boolean,
    val pinnedMessages: Boolean,
    val mentions: Boolean,
)

/** [settings] are the new defaults of [scope], made here or on another device. */
data class ScopeNotificationSettingsChange(
    val scope: NotificationScope,
    val settings: ScopeNotificationSettings,
)

/** Whether a chat notifies, by a setting of its own or by the default of its scope (td_api.tl `mute_for`). */
sealed interface ChatMute {
    /** No setting of its own: the chat follows the default of its scope. */
    data object Default : ChatMute

    /** Unmuted by a setting of its own, even while its scope is muted by default. */
    data object Unmuted : ChatMute

    /** Muted until [untilEpochSeconds] (Unix time); TDLib reports the chat unmuted when that time comes. */
    data class MutedUntil(
        val untilEpochSeconds: Long,
    ) : ChatMute

    /** Muted with no end (TDLib takes a mute of more than 366 days as forever). */
    data object MutedForever : ChatMute
}

/** What the user makes of a chat's notifications. */
sealed interface MuteChange {
    /** Mute for [seconds] from now. */
    data class MuteFor(
        val seconds: Int,
    ) : MuteChange

    data object MuteForever : MuteChange

    /** Unmute, even while the chat's scope is muted by default. */
    data object Unmute : MuteChange

    /** Drop the chat's own setting, so it follows the default of its scope again. */
    data object UseDefault : MuteChange
}

/**
 * A chat whose notifications differ from the default of its scope (td_api.tl
 * `getChatNotificationSettingsExceptions`), as the exceptions list shows it.
 *
 * @property title the chat's title; for private and secret chats the other user's name.
 * @property photo null if the chat has no photo.
 */
data class NotificationException(
    val chatId: Long,
    val type: ChatType,
    val title: String,
    val photo: ProfilePhoto?,
    val mute: ChatMute,
) {
    override fun toString() = "NotificationException(chatId=$chatId, type=$type, mute=$mute, …)"
}

/** Chat [chatId]'s notification settings changed, here or on another device: it is now [mute]. */
data class ChatMuteChange(
    val chatId: Long,
    val mute: ChatMute,
)

/**
 * How new messages reach Pingogram while it is in the background, fixed by the build's flavor
 * (`docs/adr/0003-keep-connected-foreground-service.md`).
 */
enum class PushDelivery {
    /**
     * `foss`: only TDLib's own connection, which lasts as long as Android keeps the process; the user may keep the
     * process running with a foreground service ("Keep connected", `KeepConnectedRepository`).
     */
    TdlibConnection,

    /** `gms`: Firebase Cloud Messaging wakes the app for new messages (`docs/adr/0004-push-delivery.md`). */
    Firebase,
}
