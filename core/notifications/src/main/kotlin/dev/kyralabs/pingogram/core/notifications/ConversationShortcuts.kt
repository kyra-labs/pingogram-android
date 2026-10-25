package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The long-lived conversation shortcuts of chats (Android 11+): of chats with notifications, which make Android show
 * their notifications in its conversation section, with the chat's avatar, and let the user set a conversation's
 * priority; and of the most recent chats ([publishShareTargets], [ShareTargetShortcuts]). A shortcut opens its chat
 * ([NotificationIntents.openChat]).
 *
 * Every one is a Direct Share target too (`REQUIREMENTS.md` FR-N2): it carries
 * [NotificationIntents.SHARE_TARGET_CATEGORY], which the app's share target (`res/xml/shortcuts.xml`) names, so the
 * system's share sheet lists the chat, and a share to it reaches `MainActivity` with the shortcut's id. Before Android
 * 11 there is no conversation section, and pushing a shortcut is rate-limited there, so nothing is published.
 *
 * One per account ([ConversationShortcutsFactory], `docs/adr/0031-multiple-accounts.md`): the account in [slot] has
 * its own shortcuts, whose ids carry the slot ([NotificationIntents.shortcutId]) and open the chat in that account.
 */
internal class ConversationShortcuts(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val slot: Int = 0,
) {
    /** What was last published per chat, so an unchanged shortcut is not published again on every message. */
    private val published = ConcurrentHashMap<Long, ShortcutContent>()

    /** The chats [publishShareTargets] last published. */
    @Volatile
    private var shareTargets: Set<Long> = emptySet()

    /**
     * What Android's share targets show, by chat ([ShareTarget.key] and rank): read from the device's shortcuts at
     * the first [publishShareTargets] of the process, kept as targets are pushed. Null until read.
     */
    private var shareKeys: MutableMap<Long, String>? = null

    /**
     * Publishes (or updates) the shortcut of [conversation]'s chat and returns its id; null where conversations
     * have no shortcuts, or publishing failed.
     */
    suspend fun publish(conversation: ConversationNotification): String? {
        val shortcut =
            ChatShortcut(conversation.chatId, conversation.title, conversation.chatIcon, conversation.chatPerson)
        return shortcutId(conversation.chatId).takeIf { publish(shortcut, rank = null) }
    }

    /**
     * Publishes [targets], the most recent chats, in their order (the first ranked highest), as Direct Share
     * targets, and removes the dynamic shortcuts of the chats that were published as targets before and no longer
     * are (a notification's shortcut stays cached by Android while it shows). A target Android already shows as it
     * is, published by this process or an earlier one, is left as it is, without building its avatar: so a cold
     * start does not push every target again (`docs/PERFORMANCE.md` §2). Called from one coroutine at a time.
     */
    suspend fun publishShareTargets(targets: List<ShareTarget>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val onDevice = shareKeys ?: shareKeysOnDevice().also { shareKeys = it }
        val before = shareTargets + onDevice.keys
        targets.forEachIndexed { rank, target ->
            val key = "${target.key}$RANK_SEPARATOR$rank"
            if (onDevice[target.chatId] != key && publish(target.shortcut(), rank, key)) {
                onDevice[target.chatId] = key
            }
        }
        val ids = targets.mapTo(HashSet()) { it.chatId }
        val dropped = before - ids
        shareTargets = ids
        if (dropped.isEmpty()) return
        dropped.forEach { chatId ->
            published.remove(chatId)
            onDevice.remove(chatId)
        }
        withContext(ioDispatcher) {
            try {
                ShortcutManagerCompat.removeDynamicShortcuts(context, dropped.map(::shortcutId))
            } catch (_: IllegalStateException) {
                // The user is locked: before the first unlock after a reboot.
            }
        }
    }

    /** The keys of the share targets an earlier process pushed, which Android still has, by chat. */
    private suspend fun shareKeysOnDevice(): MutableMap<Long, String> =
        withContext(ioDispatcher) {
            try {
                ShortcutManagerCompat
                    .getDynamicShortcuts(context)
                    .mapNotNull { shortcut ->
                        val chat = NotificationIntents.chatOfShortcut(shortcut.id)?.takeIf { it.slot == slot }
                        val key = shortcut.extras?.getString(KEY_EXTRA)
                        if (chat != null && key != null) chat.chatId to key else null
                    }.toMap(HashMap())
            } catch (_: IllegalStateException) {
                // The user is locked: before the first unlock after a reboot.
                HashMap()
            }
        }

    /**
     * Publishes [chat]'s shortcut, at [rank] (null: as it was), a share target's with its [key]; false where it
     * cannot be.
     */
    private suspend fun publish(
        chat: ChatShortcut,
        rank: Int?,
        key: String? = null,
    ): Boolean {
        val previous = published[chat.chatId]
        val content =
            ShortcutContent(chat.title, System.identityHashCode(chat.icon), rank ?: previous?.rank ?: 0)
        val isPublished =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (previous == content || push(chat, content.rank, key))
        if (isPublished) published[chat.chatId] = content
        return isPublished
    }

    /** Pushes the shortcut of [chat] at [rank], with a share target's [key]; false if that failed. */
    private suspend fun push(
        chat: ChatShortcut,
        rank: Int,
        key: String?,
    ): Boolean {
        val intent = NotificationIntents.openChat(context, chat.chatId, slot) ?: return false
        val id = shortcutId(chat.chatId)
        val shortcut =
            ShortcutInfoCompat
                .Builder(context, id)
                .setShortLabel(chat.title)
                .setLongLabel(chat.title)
                .setIcon(chat.icon)
                .setIntent(intent)
                .setLongLived(true)
                .setLocusId(LocusIdCompat(id))
                .setCategories(setOf(NotificationIntents.SHARE_TARGET_CATEGORY))
                .setRank(rank)
                .apply { chat.person?.let(::setPerson) }
                .apply { key?.let { setExtras(PersistableBundle().apply { putString(KEY_EXTRA, it) }) } }
                .build()
        return withContext(ioDispatcher) {
            try {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            } catch (_: IllegalStateException) {
                // The user is locked: before the first unlock after a reboot.
                false
            }
        }
    }

    /**
     * Removes the conversation shortcuts of [chatIds], cached ones too, so neither the launcher, the share sheet
     * nor the conversation section names them: chats that were locked (`docs/adr/0029-locked-chats.md`).
     */
    suspend fun remove(chatIds: Set<Long>) {
        chatIds.forEach(published::remove)
        shareTargets = shareTargets - chatIds
        withContext(ioDispatcher) {
            try {
                val ids = chatIds.map(::shortcutId)
                ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
                ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
            } catch (_: IllegalStateException) {
                // The user is locked: before the first unlock after a reboot. The next lock change tries again.
            }
        }
    }

    /** Removes every conversation shortcut of the account, cached ones too, e.g. when it logs out. */
    suspend fun removeAll() {
        published.clear()
        shareTargets = emptySet()
        shareKeys = HashMap()
        withContext(ioDispatcher) {
            try {
                val flags = ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
                val ids =
                    ShortcutManagerCompat
                        .getShortcuts(context, flags)
                        .map { it.id }
                        .filter { NotificationIntents.chatOfShortcut(it)?.slot == slot }
                if (ids.isNotEmpty()) {
                    ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
                    ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
                }
            } catch (_: IllegalStateException) {
                // The user is locked; the shortcuts open chats of an account that is gone, which then show nothing.
            }
        }
    }

    /**
     * What a shortcut shows, and its rank; the icon by identity: [AvatarIcons] hands out the same icon for the same
     * avatar.
     */
    private data class ShortcutContent(
        val title: String,
        val iconIdentity: Int,
        val rank: Int,
    )

    /** The id of chat [chatId]'s shortcut, which its notifications name too. */
    private fun shortcutId(chatId: Long) = NotificationIntents.shortcutId(chatId, slot)

    private companion object {
        /** The extra of a share target's shortcut that holds its key and rank. */
        private const val KEY_EXTRA = "dev.kyralabs.pingogram.shareTarget"
        private const val RANK_SEPARATOR = '#'
    }
}

/** Each account's [ConversationShortcuts], one per slot, which its notifications and share targets share. */
@Singleton
internal class ConversationShortcutsFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val bySlot = ConcurrentHashMap<Int, ConversationShortcuts>()

        fun of(slot: Int): ConversationShortcuts =
            bySlot.getOrPut(slot) { ConversationShortcuts(context, ioDispatcher, slot) }
    }

/**
 * A Direct Share target: chat [chatId], whose shortcut shows what [key] says (its title and avatar, in a form that is
 * the same in every process), built by [shortcut] only when it has to be pushed.
 */
internal class ShareTarget(
    val chatId: Long,
    val key: String,
    val shortcut: suspend () -> ChatShortcut,
)

/** A chat's conversation shortcut: its [title], its avatar as [icon], and the other user of a private chat. */
internal class ChatShortcut(
    val chatId: Long,
    val title: String,
    val icon: IconCompat,
    val person: Person?,
) {
    override fun toString() = "ChatShortcut(chatId=$chatId, …)"
}
