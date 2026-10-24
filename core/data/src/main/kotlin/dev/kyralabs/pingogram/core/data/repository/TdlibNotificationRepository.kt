package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toChatNotification
import dev.kyralabs.pingogram.core.data.mapper.toNotificationGroup
import dev.kyralabs.pingogram.core.data.mapper.toNotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NotificationRepository] on TDLib's notification engine (`docs/TDLIB.md`, Notifications). TDLib leaves the engine
 * off until `notification_group_count_max` is set, so this sets it, and `notification_group_size_max`, whenever the
 * account is logged in ([GROUP_COUNT_MAX], [GROUP_SIZE_MAX]); TDLib keeps them in its database, so from the next start
 * on it restores the notifications that were active before with `updateActiveNotifications`.
 *
 * Then `updateNotificationGroup` adds and removes notifications of a group (a group left without any is gone),
 * `updateNotification` changes one (an edited message), and `updateHavePendingNotifications` tells whether more are on
 * their way. Logging out drops everything, at `LoggingOut` already: the account's notifications must not outlive it.
 * The open chats come from [OpenChats], which `TdlibChatRepository` keeps.
 */
@Singleton
class TdlibNotificationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
        openChats: OpenChats,
        private val linkPreviews: SecretChatLinkPreviews,
    ) : NotificationRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, NotificationCache(), NotificationCache::reduce)

        init {
            // Each login (authState is a StateFlow, so every time it turns Ready). Not in the store: requests are not
            // sent while the caches are being created, and Ready comes later.
            client.authState
                .filter { it == AuthState.Ready }
                .onEach { enableNotifications() }
                .launchIn(scope)
        }

        override val notifications: Flow<ActiveNotifications> =
            combine(store.state, openChats.chatIds) { cache, openChatIds ->
                ActiveNotifications(cache.groups, openChatIds, cache.isRestored)
            }.distinctUntilChanged()

        override val hasPendingNotifications: Flow<Boolean> =
            store.state.map { it.hasPendingNotifications }.distinctUntilChanged()

        /**
         * `sendMessage` with a plain text: TDLib finds links, mentions and hashtags itself, with no link preview in a
         * secret chat unless allowed ([SecretChatLinkPreviews]). The draft stays.
         */
        override suspend fun reply(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
        ): Outcome<Unit> {
            val content =
                TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), linkPreviews.optionsFor(chatId), false)
            return client
                .request(TdApi.SendMessage(chatId, null, replyToMessageId?.let(::replyToMessage), null, null, content))
                .map {}
        }

        /** `viewMessages` from a notification with `force_read`, which reads them although the chat is not open. */
        override suspend fun markAsRead(
            chatId: Long,
            messageIds: List<Long>,
        ): Outcome<Unit> =
            client
                .request(TdApi.ViewMessages(chatId, messageIds.toLongArray(), TdApi.MessageSourceNotification(), true))
                .map {}

        /** `removeNotificationGroup`. */
        override suspend fun dismiss(
            groupId: Int,
            maxNotificationId: Int,
        ): Outcome<Unit> = client.request(TdApi.RemoveNotificationGroup(groupId, maxNotificationId)).map {}

        /** Turns TDLib's notification engine on; a failure leaves it as it was, and the next login tries again. */
        private suspend fun enableNotifications() {
            client.request(TdApi.SetOption(GROUP_COUNT_MAX_OPTION, TdApi.OptionValueInteger(GROUP_COUNT_MAX)))
            client.request(TdApi.SetOption(GROUP_SIZE_MAX_OPTION, TdApi.OptionValueInteger(GROUP_SIZE_MAX)))
        }

        internal companion object {
            /**
             * At most this many groups (chats) at a time, TDLib's maximum: with the summary, half of the 50
             * notifications Android shows per app.
             */
            const val GROUP_COUNT_MAX = 25L

            /** At most this many notifications per group, TDLib's default: the messages a conversation shows. */
            const val GROUP_SIZE_MAX = 10L

            const val GROUP_COUNT_MAX_OPTION = "notification_group_count_max"
            const val GROUP_SIZE_MAX_OPTION = "notification_group_size_max"
        }
    }

private data class NotificationCache(
    val groups: PersistentMap<Int, NotificationGroup> = persistentMapOf(),
    val isRestored: Boolean = false,
    val hasPendingNotifications: Boolean = false,
) {
    fun reduce(update: TdApi.Update): NotificationCache =
        when (update) {
            is TdApi.UpdateActiveNotifications -> {
                val restored = update.groups.mapNotNull { it.toNotificationGroup() }
                NotificationCache(
                    groups = restored.associateBy { it.id }.toPersistentMap(),
                    isRestored = true,
                    hasPendingNotifications = hasPendingNotifications,
                )
            }

            is TdApi.UpdateNotificationGroup -> {
                withGroupUpdate(update)
            }

            is TdApi.UpdateNotification -> {
                withNotification(update.notificationGroupId, update.notification)
            }

            is TdApi.UpdateHavePendingNotifications -> {
                val pending = update.haveDelayedNotifications || update.haveUnreceivedNotifications
                if (pending == hasPendingNotifications) this else copy(hasPendingNotifications = pending)
            }

            is TdApi.UpdateAuthorizationState -> {
                if (update.authorizationState is TdApi.AuthorizationStateLoggingOut) NotificationCache() else this
            }

            else -> {
                this
            }
        }

    /**
     * Applies the removals, then the additions, which alert unless the chat's sound is off (`notification_sound_id`
     * 0) or they were sent silently.
     */
    private fun withGroupUpdate(update: TdApi.UpdateNotificationGroup): NotificationCache {
        val type = update.type.toNotificationGroupType() ?: return this
        val removed = update.removedNotificationIds.toSet()
        val soundOn = update.notificationSoundId != 0L
        val added = update.addedNotifications.mapNotNull { it.toChatNotification(alerts = soundOn) }
        val addedIds = added.mapTo(HashSet()) { it.id }
        val kept =
            groups[update.notificationGroupId]
                ?.notifications
                .orEmpty()
                .filter { it.id !in removed && it.id !in addedIds }
        val notifications = (kept + added).sortedBy { it.id }
        val groups =
            if (notifications.isEmpty()) {
                groups.removing(update.notificationGroupId)
            } else {
                val group =
                    NotificationGroup(
                        id = update.notificationGroupId,
                        type = type,
                        chatId = update.chatId,
                        totalCount = update.totalCount,
                        notifications = notifications.toImmutableList(),
                    )
                groups.putting(group.id, group)
            }
        return copy(groups = groups, isRestored = true)
    }

    /** Replaces a shown notification, which keeps not alerting again. */
    private fun withNotification(
        groupId: Int,
        notification: TdApi.Notification,
    ): NotificationCache {
        val group = groups[groupId]
        val changed = notification.toChatNotification(alerts = false)
        if (group == null || changed == null || group.notifications.none { it.id == changed.id }) return this
        val notifications = group.notifications.map { if (it.id == changed.id) changed else it }.toImmutableList()
        return copy(groups = groups.putting(groupId, group.copy(notifications = notifications)))
    }
}
