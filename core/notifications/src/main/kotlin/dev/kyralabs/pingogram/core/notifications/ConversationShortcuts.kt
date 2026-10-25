package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.os.Build
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
 */
@Singleton
internal class ConversationShortcuts
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    ) {
        /** What was last published per chat, so an unchanged shortcut is not published again on every message. */
        private val published = ConcurrentHashMap<Long, ShortcutContent>()

        /** The chats [publishShareTargets] last published. */
        @Volatile
        private var shareTargets: Set<Long> = emptySet()

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
         * Publishes [chats], the most recent ones, in their order (the first ranked highest), as Direct Share targets,
         * and removes the dynamic shortcuts of the chats that were published here last time and no longer are (a
         * notification's shortcut stays cached by Android while it shows).
         */
        suspend fun publishShareTargets(chats: List<ChatShortcut>) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            chats.forEachIndexed { rank, chat -> publish(chat, rank) }
            val ids = chats.mapTo(HashSet()) { it.chatId }
            val dropped = shareTargets - ids
            shareTargets = ids
            if (dropped.isEmpty()) return
            dropped.forEach(published::remove)
            withContext(ioDispatcher) {
                try {
                    ShortcutManagerCompat.removeDynamicShortcuts(context, dropped.map(::shortcutId))
                } catch (_: IllegalStateException) {
                    // The user is locked: before the first unlock after a reboot.
                }
            }
        }

        /** Publishes [chat]'s shortcut, at [rank] (null: as it was); false where it cannot be. */
        private suspend fun publish(
            chat: ChatShortcut,
            rank: Int?,
        ): Boolean {
            val previous = published[chat.chatId]
            val content =
                ShortcutContent(chat.title, System.identityHashCode(chat.icon), rank ?: previous?.rank ?: 0)
            val isPublished =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (previous == content || push(chat, content.rank))
            if (isPublished) published[chat.chatId] = content
            return isPublished
        }

        /** Pushes the shortcut of [chat] at [rank]; false if that failed. */
        private suspend fun push(
            chat: ChatShortcut,
            rank: Int,
        ): Boolean {
            val intent = NotificationIntents.openChat(context, chat.chatId) ?: return false
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

        /** Removes every conversation shortcut, cached ones too, e.g. when the account logs out. */
        suspend fun removeAll() {
            published.clear()
            shareTargets = emptySet()
            withContext(ioDispatcher) {
                try {
                    val flags = ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
                    val ids = ShortcutManagerCompat.getShortcuts(context, flags).map { it.id }
                    ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                    if (ids.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
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

        companion object {
            /** The id of chat [chatId]'s shortcut, which its notifications name too. */
            fun shortcutId(chatId: Long) = NotificationIntents.shortcutId(chatId)
        }
    }

/** A chat's conversation shortcut: its [title], its avatar as [icon], and the other user of a private chat. */
internal class ChatShortcut(
    val chatId: Long,
    val title: String,
    val icon: IconCompat,
    val person: Person?,
) {
    override fun toString() = "ChatShortcut(chatId=$chatId, …)"
}
