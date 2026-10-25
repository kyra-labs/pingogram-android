package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DeviceAccountLimits
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
    /**
     * Messages: each account has a channel of its own ([idFor]), so a user can set one account's apart
     * (`docs/adr/0031-multiple-accounts.md`); the rest are the app's.
     */
    val perAccount: Boolean = false,
) {
    PrivateChats(
        "private_chats",
        R.string.notification_channel_private_chats,
        R.string.notification_channel_private_chats_description,
        NotificationManagerCompat.IMPORTANCE_HIGH,
        perAccount = true,
    ),
    Groups(
        "groups",
        R.string.notification_channel_groups,
        R.string.notification_channel_groups_description,
        NotificationManagerCompat.IMPORTANCE_HIGH,
        perAccount = true,
    ),
    Channels(
        "channels",
        R.string.notification_channel_channels,
        R.string.notification_channel_channels_description,
        NotificationManagerCompat.IMPORTANCE_DEFAULT,
        perAccount = true,
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
        perAccount = true,
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

    /**
     * This channel's id for the account in [slot]: the first account's is [id], as before there were several; another
     * account's carries its slot. Only for [perAccount] channels; the others have [id] alone.
     */
    fun idFor(slot: Int): String = if (slot == 0 || !perAccount) id else "${id}_$slot"

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
 *
 * Every account on the device has its message channels (`docs/adr/0031-multiple-accounts.md`); while more than one is
 * signed in, each account's are in a channel group of their own named after it, as Telegram's are, so the system
 * settings show them apart. A channel keeps its group once it has one (Android sets a group only on a channel without
 * one), so the first account's stay in theirs after the others left. An account that left the device takes its group
 * and channels with it.
 */
@Singleton
class NotificationChannels
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** The app's channels and the first account's, as with one account. */
        fun ensureCreated() = ensureCreated(slots = listOf(0), names = emptyMap())

        /**
         * The channels of the accounts in [slots], in channel groups named [names] (by slot) while it holds more than
         * one account; deletes those of the slots that are gone.
         */
        fun ensureCreated(
            slots: List<Int>,
            names: Map<Int, String>,
        ) {
            val manager = NotificationManagerCompat.from(context)
            val grouped = names.size > 1
            if (grouped) {
                manager.createNotificationChannelGroupsCompat(
                    names.map { (slot, name) ->
                        NotificationChannelGroupCompat.Builder(groupId(slot)).setName(name).build()
                    },
                )
            }
            val channels =
                PingogramChannel.entries.flatMap { channel ->
                    if (channel.perAccount) {
                        (slots + 0).distinct().map { slot ->
                            channel(channel, channel.idFor(slot), groupId(slot).takeIf { grouped && slot in names })
                        }
                    } else {
                        listOf(channel(channel, channel.id, group = null))
                    }
                }
            manager.createNotificationChannelsCompat(channels)
            OBSOLETE_CHANNELS.forEach(manager::deleteNotificationChannel)
            for (gone in 1 until DeviceAccountLimits.MAX_ACCOUNTS_WITH_PREMIUM) {
                if (gone in slots) continue
                PingogramChannel.entries.filter { it.perAccount }.forEach {
                    manager.deleteNotificationChannel(it.idFor(gone))
                }
                manager.deleteNotificationChannelGroup(groupId(gone))
            }
        }

        private fun channel(
            channel: PingogramChannel,
            id: String,
            group: String?,
        ): NotificationChannelCompat =
            NotificationChannelCompat
                .Builder(id, channel.importance)
                .setName(context.getString(channel.title))
                .setDescription(context.getString(channel.description))
                .setShowBadge(channel.showBadge)
                .apply {
                    group?.let(::setGroup)
                    if (channel.ringtone) {
                        setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), RINGTONE_ATTRIBUTES)
                        setVibrationEnabled(true)
                    } else if (channel == PingogramChannel.OngoingCalls) {
                        setSound(null, null)
                    }
                }.build()

        private companion object {
            /** Earlier versions' channels: "calls" rang with the notification sound ([PingogramChannel.Calls]). */
            val OBSOLETE_CHANNELS = listOf("calls")

            /** The channel group of the account in [slot]. */
            fun groupId(slot: Int) = "account_$slot"

            val RINGTONE_ATTRIBUTES: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
        }
    }
