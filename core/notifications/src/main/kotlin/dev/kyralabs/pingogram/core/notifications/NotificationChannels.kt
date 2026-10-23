package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pingogram's notification channels (`REQUIREMENTS.md` FR-N2), which users can set apart in the system settings. The
 * names never contain "Telegram" (CLAUDE.md rule 2). Ids are kept forever: Android keeps a channel's settings by id,
 * and a renamed id loses what the user chose.
 *
 * Messages and calls pop up ([NotificationManagerCompat.IMPORTANCE_HIGH]); channel posts and everything else only
 * sound. Incoming calls ring with the phone's ringtone ([ringtone]); the progress of transfers ([Transfers]) and the
 * ongoing call ([OngoingCalls]) are silent and show no badge.
 */
enum class PingogramChannel(
    val id: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val importance: Int,
    val showBadge: Boolean = true,
    /** Rings with the default ringtone, as a phone call does, rather than the notification sound. */
    val ringtone: Boolean = false,
) {
    PrivateChats(
        "private_chats",
        R.string.notification_channel_private_chats,
        R.string.notification_channel_private_chats_description,
        NotificationManagerCompat.IMPORTANCE_HIGH,
    ),
    Groups(
        "groups",
        R.string.notification_channel_groups,
        R.string.notification_channel_groups_description,
        NotificationManagerCompat.IMPORTANCE_HIGH,
    ),
    Channels(
        "channels",
        R.string.notification_channel_channels,
        R.string.notification_channel_channels_description,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    /**
     * Incoming calls, with the ringtone (`docs/adr/0018-calls-and-telecom.md`). A new id: a channel's sound cannot be
     * changed once created, and "calls" (until 0.4.0) had the notification sound; [NotificationChannels] deletes it.
     */
    Calls(
        "incoming_calls",
        R.string.notification_channel_calls,
        R.string.notification_channel_calls_description,
        NotificationManagerCompat.IMPORTANCE_HIGH,
        ringtone = true,
    ),

    /** The call the user is in (its foreground service's notification): no sound, no badge. */
    OngoingCalls(
        "ongoing_calls",
        R.string.notification_channel_ongoing_calls,
        R.string.notification_channel_ongoing_calls_description,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
        showBadge = false,
    ),
    Other(
        "other",
        R.string.notification_channel_other,
        R.string.notification_channel_other_description,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
    ),

    /** The files the user sends and downloads, while they are on their way (`TransfersWorker`, FR-N4). */
    Transfers(
        "transfers",
        R.string.notification_channel_transfers,
        R.string.notification_channel_transfers_description,
        NotificationManagerCompat.IMPORTANCE_LOW,
        showBadge = false,
    ),
    ;

    companion object {
        /**
         * The channel of a notification group: calls go to [Calls]; messages by the kind of chat, a secret chat's to
         * [PrivateChats] as its partner's are; [Other] while the chat is unknown.
         */
        fun of(
            groupType: NotificationGroupType,
            chatType: ChatType?,
        ): PingogramChannel =
            when {
                groupType == NotificationGroupType.Calls -> Calls
                chatType == null -> Other
                chatType is ChatType.Private || chatType is ChatType.Secret -> PrivateChats
                chatType is ChatType.Supergroup && chatType.isChannel -> Channels
                else -> Groups
            }

        /**
         * The channel of the messages of [scope]'s chats, whose sound, vibration and importance Settings →
         * Notifications opens in the system settings.
         */
        fun of(scope: NotificationScope): PingogramChannel =
            when (scope) {
                NotificationScope.PrivateChats -> PrivateChats
                NotificationScope.GroupChats -> Groups
                NotificationScope.ChannelChats -> Channels
            }
    }
}

/**
 * Creates the [PingogramChannel]s, the one place that decides which exist (the notification settings of a later
 * milestone will add their per-chat channels here). Creating an existing channel changes only its name and
 * description, never what the user set, so [ensureCreated] runs at every start: a few binder calls, no disk access of
 * ours.
 */
@Singleton
class NotificationChannels
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        fun ensureCreated() {
            val channels =
                PingogramChannel.entries.map { channel ->
                    NotificationChannelCompat
                        .Builder(channel.id, channel.importance)
                        .setName(context.getString(channel.title))
                        .setDescription(context.getString(channel.description))
                        .setShowBadge(channel.showBadge)
                        .apply {
                            if (channel.ringtone) {
                                setSound(
                                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                                    RINGTONE_ATTRIBUTES,
                                )
                                setVibrationEnabled(true)
                            } else if (channel == PingogramChannel.OngoingCalls) {
                                setSound(null, null)
                            }
                        }.build()
                }
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannelsCompat(channels)
            OBSOLETE_CHANNELS.forEach(manager::deleteNotificationChannel)
        }

        private companion object {
            /** Earlier versions' channels: "calls" rang with the notification sound ([PingogramChannel.Calls]). */
            val OBSOLETE_CHANNELS = listOf("calls")

            val RINGTONE_ATTRIBUTES: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
        }
    }
