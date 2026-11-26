package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.matchesWords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * The chats whose name on this phone (`REQUIREMENTS.md` FR-X20, [names]) matches a query, as search matches the top
 * people. TDLib never sees those names, so search and the sheets that pick chats find them here and list them before
 * TDLib's chats; a contact's chat, which shows the contact name, is left out.
 */
class LocalNameChats(
    private val names: Flow<Map<Long, String>>,
    private val users: UserRepository,
    private val chats: ChatRepository,
) {
    /** The first [MAX_CHATS] chats named to match [query], by name; none for a blank query. */
    suspend fun matching(
        query: String,
        locale: Locale,
    ): List<Chat> {
        val named = names.first()
        if (query.isBlank() || named.isEmpty()) return emptyList()
        val people = users.users.first()
        return named.entries
            .filter { (chatId, name) ->
                named.shownName(chatId, isContact = people[chatId]?.isContact == true) != null &&
                    name.matchesWords(query, locale)
            }.sortedBy { it.value.lowercase(locale) }
            .take(MAX_CHATS)
            .mapNotNull { chats.getChat(it.key).getOrNull() }
    }

    private companion object {
        /** As many as a screen of results shows; the rest are TDLib's to find by their titles. */
        const val MAX_CHATS = 20
    }
}
