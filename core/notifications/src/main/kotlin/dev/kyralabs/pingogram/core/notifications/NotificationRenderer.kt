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
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.ActiveNotifications
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.NotificationContent
import dev.kyralabs.pingogram.core.domain.model.NotificationGroup
import dev.kyralabs.pingogram.core.domain.model.NotificationGroupType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows TDLib's notifications (`docs/ARCHITECTURE.md`, Notifications) of every account on the device
 * (`docs/adr/0031-multiple-accounts.md`): one [AccountNotificationRenderer] per account, made as the account comes and
 * stopped, with its notifications and shortcuts gone, as it leaves the device. While more than one account is signed
 * in, each account's notifications name it by its first name, and its channels are in a group named after it
 * ([NotificationChannels]).
 *
 * `PingogramApplication` calls [start] in every process, before TDLib starts. Without permission to post (Android
 * 13+), nothing is posted; what is active shows once a later change comes after the user allowed notifications.
 */
@Singleton
class NotificationRenderer
    internal constructor(
        private val context: Context,
        private val accounts: Flow<List<NotificationAccount>>,
        private val parts: AccountNotificationParts,
        private val channels: NotificationChannels,
        private val scope: CoroutineScope,
    ) {
        @Inject
        internal constructor(
            @ApplicationContext context: Context,
            graphs: AccountGraphs,
            parts: AccountNotificationParts,
            channels: NotificationChannels,
            @ApplicationScope scope: CoroutineScope,
        ) : this(
            context = context,
            accounts = graphs.accounts.map { list -> list.map { it.toNotificationAccount() } },
            parts = parts,
            channels = channels,
            scope = scope,
        )

        private val renderers = ConcurrentHashMap<Int, Running>()

        /**
         * Creates the channels and starts rendering; call once per process. Returns at once: both run on the
         * application scope, off the main thread, the channels first.
         */
        fun start() {
            scope.launch {
                channels.ensureCreated()
                accounts.collect(::follow)
            }
        }

        /**
         * Posts group [groupId] of the account in [slot] again, silently, as it is: after an action on it failed, so
         * its notification stops showing progress. Nothing if the group or the account is gone.
         */
        suspend fun refresh(
            slot: Int,
            groupId: Int,
        ) {
            renderers[slot]?.renderer?.refresh(groupId)
        }

        /** Starts the renderers of accounts new on the device, stops those of the accounts that left it. */
        private suspend fun follow(list: List<NotificationAccount>) {
            val slots = list.mapTo(HashSet()) { it.slot }
            renderers.keys.filter { it !in slots }.forEach { slot ->
                renderers.remove(slot)?.let { running ->
                    running.job.cancel()
                    running.renderer.clear()
                }
            }
            list.filterNot { renderers.containsKey(it.slot) }.forEach { account ->
                val renderer = AccountNotificationRenderer(context, account, parts)
                val job = scope.launch { renderer.render(accountNames.map { it[account.slot] }) }
                renderers[account.slot] = Running(renderer, job)
            }
            this.list.value = list
        }

        private val list = MutableStateFlow<List<NotificationAccount>>(emptyList())

        /**
         * The first names of the signed-in accounts while there are several, by slot (none with one), each change
         * also making the channels of the accounts on the device, grouped and named after them.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private val accountNames: SharedFlow<Map<Int, String>> =
            list
                .filter { it.isNotEmpty() }
                .flatMapLatest { all ->
                    combine(all.map { it.signedInUser() }) { users -> all.map { it.slot } to users.filterNotNull() }
                }.distinctUntilChanged()
                .map { (slots, signedIn) ->
                    val names = signedIn.takeIf { it.size > 1 }.orEmpty().toMap()
                    channels.ensureCreated(slots, names.mapValues { it.value.fullName })
                    names.mapValues { it.value.shortName }
                }.distinctUntilChanged()
                .shareIn(scope, SharingStarted.Eagerly, replay = 1)

        private class Running(
            val renderer: AccountNotificationRenderer,
            val job: Job,
        )
    }

/** The account's user while it is signed in, with its slot; null otherwise. */
private fun NotificationAccount.signedInUser(): Flow<Pair<Int, AccountUserName>?> =
    combine(auth.authStep, users.myUserId, users.users) { step, myUserId, users ->
        val user = myUserId?.let(users::get)
        if (step == AuthStep.Ready && user != null) slot to AccountUserName.of(user) else null
    }.distinctUntilChanged()

/** How an account is named: by its first name in its notifications, by its full name in its channel group. */
internal data class AccountUserName(
    val shortName: String,
    val fullName: String,
) {
    companion object {
        fun of(user: User): AccountUserName {
            val full = listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
            return AccountUserName(user.firstName.ifBlank { full }, full)
        }
    }
}

/** One account's repositories that its notifications need (`docs/adr/0031-multiple-accounts.md`). */
@Suppress("LongParameterList") // One per repository.
internal class NotificationAccount(
    val slot: Int,
    val notifications: NotificationRepository,
    val auth: AuthRepository,
    val chatLock: ChatLockRepository,
    val chats: ChatRepository,
    val users: UserRepository,
    val groups: GroupRepository,
    val files: FileRepository,
) {
    override fun toString() = "NotificationAccount(slot=$slot)"
}

private fun AccountGraph.toNotificationAccount(): NotificationAccount =
    with(repositories) {
        NotificationAccount(
            slot = slot,
            notifications = notificationRepository(),
            auth = authRepository(),
            chatLock = chatLockRepository(),
            chats = chatRepository(),
            users = userRepository(),
            groups = groupRepository(),
            files = fileRepository(),
        )
    }

/** What every account's notifications are made with: the builder, the shortcuts, the avatars' settings. */
internal class AccountNotificationParts
    @Inject
    constructor(
        val builder: ConversationNotificationBuilder,
        val shortcuts: ConversationShortcutsFactory,
        val appearance: AppearanceRepository,
        @param:Dispatcher(IO) val ioDispatcher: CoroutineDispatcher,
    )

/**
 * Shows one account's TDLib notifications: one notification per notification group, drawn by
 * [ConversationNotificationBuilder], under one summary, each linked to its chat's conversation shortcut
 * ([ConversationShortcuts]). TDLib decides what is shown, after mutes, exceptions and read messages; this only renders
 * [NotificationRepository.notifications] as they change, one change at a time:
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
 * - Once TDLib has restored the notifications of the last run, notifications of this account that it no longer knows
 *   are cancelled (they belong to a previous process). A restored group that Android still shows as it is, posted by
 *   the previous process ([GroupFingerprint]), is not posted again: at a cold start that would build every avatar and
 *   shortcut anew (`docs/PERFORMANCE.md` §2).
 * - Logging out, or a start without a logged-in account, cancels the account's notifications and removes its
 *   conversation shortcuts.
 * - While several accounts are signed in, every notification names the account (its first name); a change of that
 *   posts what shows again, silently.
 *
 * Each account's notifications have tags of their own (the first account's as before there were several), so group
 * ids, which each account's TDLib counts on its own, never meet.
 */
internal class AccountNotificationRenderer(
    private val context: Context,
    private val account: NotificationAccount,
    parts: AccountNotificationParts,
) {
    private val builder = parts.builder
    private val shortcuts = parts.shortcuts.of(account.slot)
    private val factory =
        ConversationNotificationFactory(
            context = context,
            chatRepository = account.chats,
            userRepository = account.users,
            groupRepository = account.groups,
            avatars = AvatarIcons(context, account.files, parts.appearance, parts.ioDispatcher),
            slot = account.slot,
        )
    private val manager = NotificationManagerCompat.from(context)
    private val mutex = Mutex()
    private val groupTag = tagFor(GROUP_TAG, account.slot)
    private val summaryTag = tagFor(SUMMARY_TAG, account.slot)
    private val lockedTag = tagFor(LOCKED_TAG, account.slot)

    /** What each group showed last, by group id; also for groups of open chats, which are not posted. */
    private val shown = HashMap<Int, Shown>()
    private var latest = ActiveNotifications()
    private var lockedChatIds = emptySet<Long>()
    private var staleCancelled = false
    private var accountName: String? = null

    /** What an earlier process posted and Android still shows. */
    private val earlier = EarlierGroups(manager, groupTag)

    /**
     * Renders the account's notifications, named [accountNames] (null with one account), until cancelled; while the
     * account is signed out, it shows none.
     */
    suspend fun render(accountNames: Flow<String?>) =
        combine(
            account.notifications.notifications,
            account.chatLock.lockedChatIds,
            accountNames.distinctUntilChanged(),
            account.auth.authStep
                .map { it.isSignedOut() }
                .distinctUntilChanged(),
        ) { active, locked, name, signedOut -> Rendering(active, locked, name, signedOut) }
            .collect { if (it.signedOut) clear() else render(it.active, it.locked, it.name) }

    private class Rendering(
        val active: ActiveNotifications,
        val locked: Set<Long>,
        val name: String?,
        val signedOut: Boolean,
    )

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
        name: String?,
    ) = mutex.withLock {
        latest = active
        val renamed = name != accountName
        accountName = name
        // Chats locked since the last render: their conversation shortcuts would name them.
        val newlyLocked = locked - lockedChatIds
        lockedChatIds = locked
        if (newlyLocked.isNotEmpty()) shortcuts.remove(newlyLocked)
        val lockedBefore = shown.values.any { it.locked }
        var change = LockedChange(changed = renamed)
        (shown.keys - active.groups.keys).forEach { groupId ->
            if (shown.remove(groupId)?.locked == true) change += LockedChange(changed = true)
            manager.cancel(groupTag, groupId)
        }
        for (group in active.groups.values.sortedBy { it.notifications.last().date }) {
            change += render(group, active.openChatIds, locked, renamed)
        }
        // The one notification of the locked chats, while any has a group.
        if (change.changed) {
            when {
                shown.values.any { it.locked } -> {
                    notify(lockedTag, LOCKED_ID, builder.locked(change.alert, account.slot, accountName))
                }

                lockedBefore -> {
                    manager.cancel(lockedTag, LOCKED_ID)
                }
            }
        }
        updateSummary()
        if (active.isRestored && !staleCancelled) cancelStale()
    }

    /**
     * Posts, cancels or holds back [group] as it changed since it was last rendered (or posts it again where the
     * account was [renamed]), or counts it in the locked chats' notification; returns what that changed of the locked
     * chats' notification.
     */
    private suspend fun render(
        group: NotificationGroup,
        openChatIds: Set<Long>,
        locked: Set<Long>,
        renamed: Boolean,
    ): LockedChange {
        val calls = group.type == NotificationGroupType.Calls
        val isLocked = !calls && group.chatId in locked
        val visible = !calls && !isLocked && group.chatId !in openChatIds
        val previous = shown[group.id]
        val now = Shown(group, visible, isLocked)
        if (previous == now && !(renamed && now.visible)) return LockedChange()
        shown[group.id] = now
        val alert = ChatNotificationAlerts.shouldAlert(previous?.group, group)
        show(group, previous, now, alert)
        return LockedChange(changed = isLocked || previous?.locked == true, alert = isLocked && alert)
    }

    /** Posts [group] as it shows [now], or cancels what it showed before ([previous]). */
    private suspend fun show(
        group: NotificationGroup,
        previous: Shown?,
        now: Shown,
        alert: Boolean,
    ) {
        // A group seen first now, quietly: TDLib restoring the last run's groups.
        val restored = now.visible && previous == null && !alert
        when {
            restored && earlier.isStillShown(group, accountName) -> Unit
            now.visible -> post(group, alert)
            previous?.visible == true -> manager.cancel(groupTag, group.id)
        }
    }

    private suspend fun post(
        group: NotificationGroup,
        alert: Boolean,
    ) {
        val conversation = factory.create(group)
        val shortcutId = shortcuts.publish(conversation)
        val notification = builder.build(conversation, alert, shortcutId, accountName)
        notification.extras.putString(GroupFingerprint.EXTRA, GroupFingerprint.of(group, accountName))
        notify(groupTag, group.id, notification)
    }

    private fun updateSummary() {
        val posted = shown.values.filter { it.visible || it.locked }
        if (posted.isEmpty()) {
            manager.cancel(summaryTag, SUMMARY_ID)
        } else {
            notify(
                summaryTag,
                SUMMARY_ID,
                builder.summary(posted.sumOf { it.group.totalCount }, account.slot, accountName),
            )
        }
    }

    /** Cancels the notifications of groups TDLib no longer has, left by an earlier process. */
    private fun cancelStale() {
        staleCancelled = true
        earlier.restored()
        manager.activeNotifications
            .filter { it.tag == groupTag && shown[it.id]?.visible != true }
            .forEach { manager.cancel(groupTag, it.id) }
        if (shown.values.none { it.locked }) manager.cancel(lockedTag, LOCKED_ID)
    }

    /** Cancels the account's notifications and removes its conversation shortcuts. */
    suspend fun clear() =
        mutex.withLock {
            shown.clear()
            latest = ActiveNotifications()
            staleCancelled = false
            earlier.reset()
            manager.activeNotifications
                .filter { it.tag == groupTag || it.tag == summaryTag || it.tag == lockedTag }
                .forEach { manager.cancel(it.tag, it.id) }
            shortcuts.removeAll()
        }

    // Checked right here: on Android 13+ posting needs the runtime permission, before it never does.
    @SuppressLint("MissingPermission")
    private fun notify(
        tag: String,
        id: Int,
        notification: Notification,
    ) {
        val canPost =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (canPost) manager.notify(tag, id, notification)
    }

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

    companion object {
        /** The tag of the groups' notifications, whose ids are TDLib's group ids. */
        const val GROUP_TAG = "group"
        const val SUMMARY_TAG = "summary"
        const val SUMMARY_ID = 0
        const val LOCKED_ID = 0
        const val LOCKED_TAG = "locked"

        /** The account in [slot]'s [tag]: the first account's as before there were several. */
        fun tagFor(
            tag: String,
            slot: Int,
        ) = if (slot == 0) tag else "${tag}_$slot"
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
 * count, the account's name while it shows one, and each notification with its message's last edit and whether it
 * may show a preview. The same in every
 * process, so a restored group can be compared with what an earlier process posted. Names and avatars changed while no
 * process ran show with the group's next change.
 */
internal object GroupFingerprint {
    const val EXTRA = "dev.kyralabs.pingogram.group"

    fun of(
        group: NotificationGroup,
        accountName: String? = null,
    ): String =
        buildString {
            accountName?.let { append(it).append('|') }
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

    /**
     * Whether an earlier process posted [group] as it is now, named [accountName], and Android still shows it; asks
     * once per group.
     */
    fun isStillShown(
        group: NotificationGroup,
        accountName: String? = null,
    ): Boolean {
        val fingerprints = fingerprints ?: read().also { fingerprints = it }
        return fingerprints.remove(group.id) == GroupFingerprint.of(group, accountName)
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
