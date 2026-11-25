package dev.kyralabs.pingogram.core.domain.repository

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.Flow

/**
 * The user's own names for chats (`REQUIREMENTS.md` FR-X20) that show where the chats' titles show, by chat id: the
 * names kept on this phone, none while Settings → Chats → "Notes and names on this phone" is off. A contact's private
 * chat shows Telegram's contact name instead, whatever is kept for it ([shownName]).
 */
interface LocalChatNamesRepository {
    /** The names, then every change. */
    val names: Flow<ImmutableMap<Long, String>>
}

/**
 * The name chat [chatId] shows instead of its title: the user's own, unless it is the private chat of a contact
 * ([isContact]), whose contact name shows and whose own name stays kept, hidden (owner decision 2026-11-25); null for
 * the chat's title.
 */
fun Map<Long, String>.shownName(
    chatId: Long,
    isContact: Boolean,
): String? = if (isContact) null else this[chatId]
