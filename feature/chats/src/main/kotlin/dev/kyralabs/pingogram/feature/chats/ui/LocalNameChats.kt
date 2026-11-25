package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.shownName
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * The chats whose name on this phone (`REQUIREMENTS.md` FR-X20) matches a query, as the header matches the top
 * people. TDLib never sees those names, so search finds them here and lists them before TDLib's chats; a contact's
 * chat, which shows the contact name, is left out.
 */
internal class LocalNameChats(
    private val localChatNames: LocalChatNamesRepository,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
) {
    /** The first [MAX_CHATS] chats named to match [query], by name; none for a blank query. */
    suspend fun matching(
        query: String,
        locale: Locale,
    ): List<Chat> {
        val names = localChatNames.names.first()
        if (query.isBlank() || names.isEmpty()) return emptyList()
        val users = userRepository.users.first()
        return names.entries
            .filter { (chatId, name) ->
                names.shownName(chatId, isContact = users[chatId]?.isContact == true) != null &&
                    name.matchesWords(query, locale)
            }.sortedBy { it.value.lowercase(locale) }
            .take(MAX_CHATS)
            .mapNotNull { chatRepository.getChat(it.key).getOrNull() }
    }

    private companion object {
        /** As many as a screen of results shows; the rest are TDLib's to find by their titles. */
        const val MAX_CHATS = 20
    }
}
