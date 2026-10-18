package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatType
import org.drinkless.tdlib.TdApi

/**
 * The kinds of chats with default notification settings of their own (td_api.tl `NotificationSettingsScope`). A chat
 * without its own mute setting is muted when the default of its scope is.
 */
internal enum class NotificationScope {
    PrivateChats,
    GroupChats,
    ChannelChats,
}

/**
 * Whether the chat's own setting mutes it; null if the chat follows the default of its [NotificationScope]. TDLib
 * reports a new setting when a mute for a limited time ends, so a positive `muteFor` means muted now.
 */
internal val TdApi.ChatNotificationSettings.ownMute: Boolean?
    get() = if (useDefaultMuteFor) null else muteFor > 0

/**
 * These settings with a mute of the chat's own: forever if [isMuted] (TDLib takes more than 366 days as forever,
 * `get_mute_until` in td/telegram/DialogNotificationSettings.cpp), else none, even while the scope is muted by
 * default. Every other setting is kept, as `setChatNotificationSettings` replaces them all.
 */
internal fun TdApi.ChatNotificationSettings.withOwnMute(isMuted: Boolean) =
    TdApi.ChatNotificationSettings(
        false,
        if (isMuted) MUTE_FOREVER else 0,
        useDefaultSound,
        soundId,
        useDefaultShowPreview,
        showPreview,
        useDefaultMuteStories,
        muteStories,
        useDefaultStorySound,
        storySoundId,
        useDefaultShowStoryPoster,
        showStoryPoster,
        useDefaultDisablePinnedMessageNotifications,
        disablePinnedMessageNotifications,
        useDefaultDisableMentionNotifications,
        disableMentionNotifications,
    )

/** A `mute_for` TDLib turns into "muted forever". */
internal const val MUTE_FOREVER = Int.MAX_VALUE

/** Whether the default of a scope mutes its chats; as for chats, TDLib reports when a limited mute ends. */
internal val TdApi.ScopeNotificationSettings.isMuted: Boolean
    get() = muteFor > 0

/** Whether a chat of [type] with [ownMute] is muted while [mutedScopes] are muted by default. */
internal fun resolveMute(
    ownMute: Boolean?,
    type: ChatType,
    mutedScopes: Set<NotificationScope>,
): Boolean = ownMute ?: (type.notificationScope in mutedScopes)

private val ChatType.notificationScope: NotificationScope
    get() =
        when (this) {
            is ChatType.Private, is ChatType.Secret -> NotificationScope.PrivateChats
            is ChatType.BasicGroup -> NotificationScope.GroupChats
            is ChatType.Supergroup -> if (isChannel) NotificationScope.ChannelChats else NotificationScope.GroupChats
        }

/** Null for a scope added by a newer TDLib. */
internal fun TdApi.NotificationSettingsScope.toNotificationScope(): NotificationScope? =
    when (this) {
        is TdApi.NotificationSettingsScopePrivateChats -> NotificationScope.PrivateChats
        is TdApi.NotificationSettingsScopeGroupChats -> NotificationScope.GroupChats
        is TdApi.NotificationSettingsScopeChannelChats -> NotificationScope.ChannelChats
        else -> null
    }
