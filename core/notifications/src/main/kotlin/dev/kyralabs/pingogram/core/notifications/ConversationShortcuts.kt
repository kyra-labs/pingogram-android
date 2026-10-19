package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.os.Build
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The long-lived conversation shortcuts of chats with notifications (Android 11+), which make Android show their
 * notifications in its conversation section, with the chat's avatar, and let the user set a conversation's priority.
 * A shortcut opens its chat ([NotificationIntents.openChat]).
 *
 * Pingogram declares no share target yet (sharing into a chat needs a chat picker and a prefilled composer), so these
 * are not direct share targets. Before Android 11 there is no conversation section, and pushing a shortcut is
 * rate-limited there, so nothing is published.
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

        /**
         * Publishes (or updates) the shortcut of [conversation]'s chat and returns its id; null where conversations
         * have no shortcuts, or publishing failed.
         */
        suspend fun publish(conversation: ConversationNotification): String? {
            val id = shortcutId(conversation.chatId)
            val content = ShortcutContent(conversation.title, System.identityHashCode(conversation.chatIcon))
            val published =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    (this.published[conversation.chatId] == content || push(conversation, id))
            if (published) this.published[conversation.chatId] = content
            return id.takeIf { published }
        }

        /** Pushes the shortcut [id] of [conversation]'s chat; false if that failed. */
        private suspend fun push(
            conversation: ConversationNotification,
            id: String,
        ): Boolean {
            val intent = NotificationIntents.openChat(context, conversation.chatId) ?: return false
            val shortcut =
                ShortcutInfoCompat
                    .Builder(context, id)
                    .setShortLabel(conversation.title)
                    .setLongLabel(conversation.title)
                    .setIcon(conversation.chatIcon)
                    .setIntent(intent)
                    .setLongLived(true)
                    .setLocusId(LocusIdCompat(id))
                    .apply { conversation.chatPerson?.let(::setPerson) }
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

        /** Removes every conversation shortcut, cached ones too, e.g. when the account logs out. */
        suspend fun removeAll() {
            published.clear()
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

        /** What a shortcut shows; the icon by identity: [AvatarIcons] hands out the same icon for the same avatar. */
        private data class ShortcutContent(
            val title: String,
            val iconIdentity: Int,
        )

        companion object {
            /** The id of chat [chatId]'s shortcut, which its notifications name too. */
            fun shortcutId(chatId: Long) = "chat_$chatId"
        }
    }
