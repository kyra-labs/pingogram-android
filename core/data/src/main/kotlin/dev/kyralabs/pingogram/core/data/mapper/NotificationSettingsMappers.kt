package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import org.drinkless.tdlib.TdApi

// The notification settings of Settings → Notifications: the defaults of the scopes and the chats' own mutes.

internal fun NotificationScope.toTdScope(): TdApi.NotificationSettingsScope =
    when (this) {
        NotificationScope.PrivateChats -> TdApi.NotificationSettingsScopePrivateChats()
        NotificationScope.GroupChats -> TdApi.NotificationSettingsScopeGroupChats()
        NotificationScope.ChannelChats -> TdApi.NotificationSettingsScopeChannelChats()
    }

internal fun TdApi.ScopeNotificationSettings.toScopeNotificationSettings() =
    ScopeNotificationSettings(
        isMuted = isMuted,
        showPreview = showPreview,
        pinnedMessages = !disablePinnedMessageNotifications,
        mentions = !disableMentionNotifications,
    )

/**
 * These defaults with what Pingogram changes taken from [settings]; the sounds and the stories' settings stay, as
 * `setScopeNotificationSettings` replaces them all. Muted means forever ([MUTE_FOREVER]); a limited mute that is still
 * running stays as it is.
 */
internal fun TdApi.ScopeNotificationSettings.with(settings: ScopeNotificationSettings) =
    TdApi.ScopeNotificationSettings(
        when {
            !settings.isMuted -> 0
            isMuted -> muteFor
            else -> MUTE_FOREVER
        },
        soundId,
        settings.showPreview,
        useDefaultMuteStories,
        muteStories,
        storySoundId,
        showStoryPoster,
        !settings.pinnedMessages,
        !settings.mentions,
    )

/**
 * The chat's mute as of [nowEpochSeconds]: TDLib's `mute_for` is the time left. More than 366 days is forever, as
 * TDLib takes it (`get_mute_until`, td/telegram/DialogNotificationSettings.cpp).
 */
internal fun TdApi.ChatNotificationSettings.toChatMute(nowEpochSeconds: Long): ChatMute =
    when {
        useDefaultMuteFor -> ChatMute.Default
        muteFor <= 0 -> ChatMute.Unmuted
        muteFor > MAX_LIMITED_MUTE_SECONDS -> ChatMute.MutedForever
        else -> ChatMute.MutedUntil(nowEpochSeconds + muteFor)
    }

/**
 * These settings with [change] applied to the mute; every other setting is kept, as `setChatNotificationSettings`
 * replaces them all.
 */
internal fun TdApi.ChatNotificationSettings.with(change: MuteChange): TdApi.ChatNotificationSettings {
    val (useDefault, muteFor) =
        when (change) {
            is MuteChange.MuteFor -> false to change.seconds

            MuteChange.MuteForever -> false to MUTE_FOREVER

            MuteChange.Unmute -> false to 0

            // TDLib ignores mute_for while use_default_mute_for is set.
            MuteChange.UseDefault -> true to 0
        }
    return TdApi.ChatNotificationSettings(
        useDefault,
        muteFor,
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
}

/** The longest mute TDLib keeps as limited: 366 days. */
private const val MAX_LIMITED_MUTE_SECONDS = 366 * 24 * 60 * 60
