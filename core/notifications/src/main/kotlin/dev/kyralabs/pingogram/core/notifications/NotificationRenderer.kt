package dev.kyralabs.pingogram.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows TDLib's notifications (`docs/ARCHITECTURE.md`, Notifications): one notification per notification group, drawn
 * by [ConversationNotificationBuilder], under one summary, each linked to its chat's conversation shortcut
 * ([ConversationShortcuts]). TDLib decides what is shown, after mutes, exceptions and read messages; this only renders
 * [NotificationRepository.notifications] as they change, on the application scope, one change at a time:
 *
 * - A group that changed is posted again; one TDLib removed is cancelled. Only notifications not seen before may alert
 *   ([ChatNotificationAlerts]); restored ones never do.
 * - The groups of chats whose conversation is on screen are not shown at all: the user sees the messages arrive, and
 *   TDLib removes them as they are read. One still unread when the chat leaves the screen shows then, silently.
 * - Groups of locked chats ([ChatLockRepository], `docs/adr/0029-locked-chats.md`) are not shown as conversations:
 *   one notification stands for all of them, "New message" without a name, sender or text
 *   ([ConversationNotificationBuilder.locked]), and their conversation shortcuts go. A chat locked while its
 *   notification shows has it replaced at once.
 * - Calls groups (an incoming call) are not shown either: `feature:calls` rings with its own call notification
 *   ([CallNotificationBuilder]); a missed call reaches the chat's group as a message.
 * - Once TDLib has restored the notifications of the last run, notifications of this app that it no longer knows are
 *   cancelled (they belong to a previous process).
 * - Logging out, or a start without a logged-in account, cancels everything and removes the conversation shortcuts.
 *
 * `PingogramApplication` calls [start] in every process, before TDLib starts. Without permission to post (Android
 * 13+), nothing is posted; what is active shows once a later change comes after the user allowed notifications.
 */
@Singleton
@Suppress("LongParameterList") // Injected: the repositories, the parts that build and post, the app's scope.
class NotificationRenderer
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val notificationRepository: NotificationRepository,
        private val authRepository: AuthRepository,
        private val chatLock: ChatLockRepository,
        private val factory: ConversationNotificationFactory,
        private val builder: ConversationNotificationBuilder,
        private val shortcuts: ConversationShortcuts,
        private val channels: NotificationChannels,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private val manager = NotificationManagerCompat.from(context)
        private val mutex = Mutex()

        /** What each group showed last, by group id; also for groups of open chats, which are not posted. */
        private val shown = HashMap<Int, Shown>()
        private var latest = ActiveNotifications()
        private var lockedChatIds = emptySet<Long>()
        private var staleCancelled = false

        /**
         * Creates the channels and starts rendering; call once per process. Returns at once: both run on the
         * application scope, off the main thread, the channels first.
         */
        fun start() {
            scope.launch {
                channels.ensureCreated()
                combine(notificationRepository.notifications, chatLock.lockedChatIds, ::Pair)
                    .collect { (active, locked) -> render(active, locked) }
            }
            scope.launch {
                authRepository.authStep.collect { step -> if (step.isSignedOut()) clearAccount() }
            }
        }

        /**
         * Posts group [groupId] again, silently, as it is: after an action on it failed, so its notification stops
         * showing progress. Nothing if the group is gone.
         */
        suspend fun refresh(groupId: Int) =
            mutex.withLock {
                val group = latest.groups[groupId] ?: return@withLock
                if (shown[groupId]?.visible == true) post(group, alert = false)
            }

        private suspend fun render(
            active: ActiveNotifications,
            locked: Set<Long>,
        ) = mutex.withLock {
            latest = active
            // Chats locked since the last render: their conversation shortcuts would name them.
            val newlyLocked = locked - lockedChatIds
            lockedChatIds = locked
            if (newlyLocked.isNotEmpty()) shortcuts.remove(newlyLocked)
            val lockedBefore = shown.values.any { it.locked }
            var change = LockedChange()
            (shown.keys - active.groups.keys).forEach { groupId ->
                if (shown.remove(groupId)?.locked == true) change += LockedChange(changed = true)
                manager.cancel(GROUP_TAG, groupId)
            }
            for (group in active.groups.values.sortedBy { it.notifications.last().date }) {
                change += renderGroup(group, active.openChatIds, locked)
            }
            // The one notification of the locked chats, while any has a group.
            if (change.changed) {
                when {
                    shown.values.any { it.locked } -> notify(LOCKED_TAG, LOCKED_ID, builder.locked(change.alert))
                    lockedBefore -> manager.cancel(LOCKED_TAG, LOCKED_ID)
                }
            }
            updateSummary()
            if (active.isRestored && !staleCancelled) cancelStale()
        }

        /**
         * Posts [group] as a conversation, cancels it, or counts it in the locked chats' notification, as it changed
         * since it last showed; returns what that changed of the locked chats' notification.
         */
        private suspend fun renderGroup(
            group: NotificationGroup,
            openChatIds: Set<Long>,
            locked: Set<Long>,
        ): LockedChange {
            val calls = group.type == NotificationGroupType.Calls
            val isLocked = !calls && group.chatId in locked
            val visible = !calls && !isLocked && group.chatId !in openChatIds
            val previous = shown[group.id]
            val now = Shown(group, visible, isLocked)
            if (previous == now) return LockedChange()
            shown[group.id] = now
            val alert = ChatNotificationAlerts.shouldAlert(previous?.group, group)
            when {
                visible -> post(group, alert)
                previous?.visible == true -> manager.cancel(GROUP_TAG, group.id)
            }
            return LockedChange(changed = isLocked || previous?.locked == true, alert = isLocked && alert)
        }

        private suspend fun post(
            group: NotificationGroup,
            alert: Boolean,
        ) {
            val conversation = factory.create(group)
            val shortcutId = shortcuts.publish(conversation)
            notify(GROUP_TAG, group.id, builder.build(conversation, alert, shortcutId))
        }

        private fun updateSummary() {
            val posted = shown.values.filter { it.visible || it.locked }
            if (posted.isEmpty()) {
                manager.cancel(SUMMARY_TAG, SUMMARY_ID)
            } else {
                notify(SUMMARY_TAG, SUMMARY_ID, builder.summary(posted.sumOf { it.group.totalCount }))
            }
        }

        /** Cancels the notifications of groups TDLib no longer has, left by an earlier process. */
        private fun cancelStale() {
            staleCancelled = true
            manager.activeNotifications
                .filter { it.tag == GROUP_TAG && shown[it.id]?.visible != true }
                .forEach { manager.cancel(GROUP_TAG, it.id) }
            if (shown.values.none { it.locked }) manager.cancel(LOCKED_TAG, LOCKED_ID)
        }

        private suspend fun clearAccount() =
            mutex.withLock {
                shown.clear()
                latest = ActiveNotifications()
                staleCancelled = false
                manager.cancelAll()
                shortcuts.removeAll()
            }

        // Checked right here: on Android 13+ posting needs the runtime permission, before it never does.
        @SuppressLint("MissingPermission")
        private fun notify(
            tag: String,
            id: Int,
            notification: Notification,
        ) {
            if (!canPost()) return
            manager.notify(tag, id, notification)
        }

        private fun canPost(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * A group as last rendered: whether it was posted as a conversation ([visible]), counted in the locked chats'
         * notification ([locked]), or held back for its open chat.
         */
        private data class Shown(
            val group: NotificationGroup,
            val visible: Boolean,
            val locked: Boolean,
        )

        /** Whether rendering changed the locked chats' notification, and whether a new message should alert. */
        private data class LockedChange(
            val changed: Boolean = false,
            val alert: Boolean = false,
        ) {
            operator fun plus(other: LockedChange) = LockedChange(changed || other.changed, alert || other.alert)
        }

        private companion object {
            /** The tag of the groups' notifications, whose ids are TDLib's group ids. */
            const val GROUP_TAG = "group"
            const val SUMMARY_TAG = "summary"
            const val SUMMARY_ID = 0

            /** The tag and id of the locked chats' one notification. */
            const val LOCKED_TAG = "locked"
            const val LOCKED_ID = 0
        }
    }

/**
 * Whether a group's new state should alert ([ChatNotificationAlerts.shouldAlert]): only when it holds an alerting
 * notification that [previous], what the group showed before (null if nothing), did not.
 */
internal object ChatNotificationAlerts {
    fun shouldAlert(
        previous: NotificationGroup?,
        group: NotificationGroup,
    ): Boolean {
        val seen = previous?.notifications.orEmpty().mapTo(HashSet()) { it.id }
        return group.notifications.any { it.alerts && it.id !in seen }
    }
}

/** A step without a logged-in account: its notifications and shortcuts must go. */
private fun AuthStep.isSignedOut(): Boolean = this != AuthStep.Initializing && this != AuthStep.Ready
