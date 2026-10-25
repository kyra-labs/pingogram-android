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
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
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
 * - Calls groups (an incoming call) are not shown either: `feature:calls` rings with its own call notification
 *   ([CallNotificationBuilder]); a missed call reaches the chat's group as a message.
 * - Once TDLib has restored the notifications of the last run, notifications of this app that it no longer knows are
 *   cancelled (they belong to a previous process). A restored group that Android still shows as it is, posted by the
 *   previous process ([GroupFingerprint]), is not posted again: at a cold start that would build every avatar and
 *   shortcut anew (`docs/PERFORMANCE.md` §2).
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
        private var staleCancelled = false

        /** What an earlier process posted and Android still shows. */
        private val earlier = EarlierGroups(manager, GROUP_TAG)

        /**
         * Creates the channels and starts rendering; call once per process. Returns at once: both run on the
         * application scope, off the main thread, the channels first.
         */
        fun start() {
            scope.launch {
                channels.ensureCreated()
                notificationRepository.notifications.collect { render(it) }
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
                if (group.chatId !in latest.openChatIds) post(group, alert = false)
            }

        private suspend fun render(active: ActiveNotifications) =
            mutex.withLock {
                latest = active
                (shown.keys - active.groups.keys).forEach { groupId ->
                    shown.remove(groupId)
                    manager.cancel(GROUP_TAG, groupId)
                }
                for (group in active.groups.values.sortedBy { it.notifications.last().date }) {
                    render(group, active.openChatIds)
                }
                updateSummary()
                if (active.isRestored && !staleCancelled) cancelStale()
            }

        /** Posts, cancels or holds back [group] as it changed since it was last rendered. */
        private suspend fun render(
            group: NotificationGroup,
            openChatIds: Set<Long>,
        ) {
            val visible = group.chatId !in openChatIds && group.type != NotificationGroupType.Calls
            val previous = shown[group.id]
            if (previous != null && previous.group == group && previous.visible == visible) return
            shown[group.id] = Shown(group, visible)
            val alert = ChatNotificationAlerts.shouldAlert(previous?.group, group)
            // A group seen first now, quietly: TDLib restoring the last run's groups.
            val restored = visible && previous == null && !alert
            when {
                restored && earlier.isStillShown(group) -> Unit
                visible -> post(group, alert)
                previous?.visible == true -> manager.cancel(GROUP_TAG, group.id)
            }
        }

        private suspend fun post(
            group: NotificationGroup,
            alert: Boolean,
        ) {
            val conversation = factory.create(group)
            val shortcutId = shortcuts.publish(conversation)
            val notification = builder.build(conversation, alert, shortcutId)
            notification.extras.putString(GroupFingerprint.EXTRA, GroupFingerprint.of(group))
            notify(GROUP_TAG, group.id, notification)
        }

        private fun updateSummary() {
            val visible = shown.values.filter { it.visible }
            if (visible.isEmpty()) {
                manager.cancel(SUMMARY_TAG, SUMMARY_ID)
            } else {
                notify(SUMMARY_TAG, SUMMARY_ID, builder.summary(visible.sumOf { it.group.totalCount }))
            }
        }

        /** Cancels the notifications of groups TDLib no longer has, left by an earlier process. */
        private fun cancelStale() {
            staleCancelled = true
            earlier.restored()
            manager.activeNotifications
                .filter { it.tag == GROUP_TAG && shown[it.id]?.visible != true }
                .forEach { manager.cancel(GROUP_TAG, it.id) }
        }

        private suspend fun clearAccount() =
            mutex.withLock {
                shown.clear()
                latest = ActiveNotifications()
                staleCancelled = false
                earlier.reset()
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

        /** A group as last rendered, and whether it was posted ([visible]) or held back for its open chat. */
        private class Shown(
            val group: NotificationGroup,
            val visible: Boolean,
        )

        private companion object {
            /** The tag of the groups' notifications, whose ids are TDLib's group ids. */
            const val GROUP_TAG = "group"
            const val SUMMARY_TAG = "summary"
            const val SUMMARY_ID = 0
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

/**
 * What a group's notification shows, as a string kept in the notification's extras ([EXTRA]): its kind, chat and
 * count, and each notification with its message's last edit and whether it may show a preview. The same in every
 * process, so a restored group can be compared with what an earlier process posted. Names and avatars changed while no
 * process ran show with the group's next change.
 */
internal object GroupFingerprint {
    const val EXTRA = "dev.kyralabs.pingogram.group"

    fun of(group: NotificationGroup): String =
        buildString {
            append(group.type.name)
                .append(':')
                .append(group.chatId)
                .append(':')
                .append(group.totalCount)
            for (notification in group.notifications) {
                append(';').append(notification.id).append(',').append(notification.date)
                when (val content = notification.content) {
                    is NotificationContent.NewMessage -> {
                        append(',').append(content.message.editDate).append(',').append(content.showPreview)
                    }

                    is NotificationContent.NewPushMessage -> {
                        append(",push")
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
}

/**
 * The groups an earlier process posted that Android still shows, by group id and [GroupFingerprint], read from Android
 * at the first question and given up once TDLib restored the groups ([restored]): a group posted after that is new.
 */
internal class EarlierGroups(
    private val manager: NotificationManagerCompat,
    private val tag: String,
) {
    private var fingerprints: MutableMap<Int, String>? = null

    /** Whether an earlier process posted [group] as it is now, and Android still shows it; asks once per group. */
    fun isStillShown(group: NotificationGroup): Boolean {
        val fingerprints = fingerprints ?: read().also { fingerprints = it }
        return fingerprints.remove(group.id) == GroupFingerprint.of(group)
    }

    /** TDLib restored the groups of the last run: what Android shows from before is either rendered or stale now. */
    fun restored() {
        fingerprints = HashMap()
    }

    /** The account logged out: everything was cancelled, the next login starts afresh. */
    fun reset() {
        fingerprints = null
    }

    private fun read(): MutableMap<Int, String> =
        manager.activeNotifications
            .filter { it.tag == tag }
            .mapNotNull { posted ->
                posted.notification.extras
                    .getString(GroupFingerprint.EXTRA)
                    ?.let { posted.id to it }
            }.toMap(HashMap())
}

/** A step without a logged-in account: its notifications and shortcuts must go. */
private fun AuthStep.isSignedOut(): Boolean = this != AuthStep.Initializing && this != AuthStep.Ready
