package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SearchRepository] on TDLib's searches. Nothing is cached, so it is no `TdlibUpdateCache`: TDLib announces every
 * chat, user and message it finds before it answers, and the chats come from [ChatRepository.getChat], from memory
 * where the chat cache has them already.
 *
 * - **Chats**: `searchChats` (TDLib's index of the chats on the device) and `searchChatsOnServer`, sent together; the
 *   local answer goes out first.
 * - **Contacts**: `searchContacts`, then `createPrivateChat` for each user, which TDLib answers from memory for a
 *   contact.
 * - **Public chats**: `searchPublicChats`; TDLib leaves out the user's chats and contacts.
 * - **Messages**: `searchMessages` in every chat list (`chat_list` null, so the archive too), paged by TDLib's offset.
 * - **Recently found chats**: `searchRecentlyFoundChats` with an empty query, which returns the whole list (TDLib has
 *   no `getRecentlyFoundChats`), and `add`/`remove`/`clearRecentlyFoundChats`.
 * - **Top people**: `getTopChats(topChatCategoryUsers)`.
 *
 * Every query is trimmed. TDLib answers 400 for what it cannot search (a username prefix too short to resolve, top
 * chats while their ranking is off); that finds nothing instead of failing.
 */
@Singleton
class TdlibSearchRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val chatRepository: ChatRepository,
    ) : SearchRepository {
        override fun searchChats(query: String): Flow<Outcome<ImmutableList<Chat>>> =
            flow {
                val text = query.trim()
                if (text.isEmpty()) {
                    emit(Outcome.Success(persistentListOf()))
                    return@flow
                }
                coroutineScope {
                    val server =
                        async { client.request(TdApi.SearchChatsOnServer(text, null, CHAT_LIMIT)).orNothing(::noChats) }
                    val local = client.request(TdApi.SearchChats(text, null, CHAT_LIMIT)).orNothing(::noChats)
                    val localIds =
                        local
                            .getOrNull()
                            ?.chatIds
                            ?.toList()
                            .orEmpty()
                    val localChats = chatRepository.chatsOf(localIds)
                    if (local is Outcome.Success) emit(Outcome.Success(localChats))
                    when (val found = server.await()) {
                        is Outcome.Success -> {
                            val serverIds = found.value.chatIds.filterNot { it in localIds }
                            emit(Outcome.Success((localChats + chatRepository.chatsOf(serverIds)).toImmutableList()))
                        }

                        is Outcome.Failure -> {
                            if (localChats.isEmpty()) emit(local as? Outcome.Failure ?: found)
                        }
                    }
                }
            }

        override suspend fun searchContacts(query: String): Outcome<ImmutableList<Chat>> {
            val text = query.trim()
            if (text.isEmpty()) return Outcome.Success(persistentListOf())
            val found = client.request(TdApi.SearchContacts(text, CONTACT_LIMIT)).orNothing(::noUsers)
            return found.map { users ->
                users.userIds
                    .toList()
                    .distinct()
                    .mapNotNull { privateChat(it) }
                    .toImmutableList()
            }
        }

        /** The private chat with user [userId], created on the device if needed; null if that fails. */
        private suspend fun privateChat(userId: Long): Chat? {
            val created = client.request(TdApi.CreatePrivateChat(userId, false)).getOrNull() ?: return null
            return chatRepository.getChat(created.id).getOrNull()
        }

        override suspend fun searchPublicChats(query: String): Outcome<ImmutableList<Chat>> {
            val text = query.trim()
            if (text.isEmpty()) return Outcome.Success(persistentListOf())
            return chatRepository.chatsFound(client.request(TdApi.SearchPublicChats(text, null)).orNothing(::noChats))
        }

        override suspend fun searchMessages(
            query: String,
            offset: String?,
        ): Outcome<FoundMessages> {
            val text = query.trim()
            if (text.isEmpty()) return Outcome.Success(FoundMessages(persistentListOf(), nextOffset = null, null))
            val request =
                TdApi.SearchMessages(null, text, offset.orEmpty(), MESSAGE_PAGE_SIZE, null, null, 0, 0)
            return when (val found = client.request(request).orNothing(::noMessages)) {
                is Outcome.Success -> Outcome.Success(chatRepository.foundMessages(found.value))
                is Outcome.Failure -> found
            }
        }

        override suspend fun recentChats(): Outcome<ImmutableList<Chat>> =
            chatRepository.chatsFound(
                client.request(TdApi.SearchRecentlyFoundChats("", null, SearchRepository.RECENT_LIMIT)),
            )

        override suspend fun addRecentChat(chatId: Long): Outcome<Unit> =
            client.request(TdApi.AddRecentlyFoundChat(chatId)).map {}

        override suspend fun removeRecentChat(chatId: Long): Outcome<Unit> =
            client.request(TdApi.RemoveRecentlyFoundChat(chatId)).map {}

        override suspend fun clearRecentChats(): Outcome<Unit> = client.request(TdApi.ClearRecentlyFoundChats()).map {}

        override suspend fun topPeople(): Outcome<ImmutableList<Chat>> =
            chatRepository.chatsFound(
                client
                    .request(TdApi.GetTopChats(TdApi.TopChatCategoryUsers(), SearchRepository.TOP_PEOPLE_LIMIT))
                    .orNothing(::noChats),
            )
    }

/** The chats of [found], in its order. */
private suspend fun ChatRepository.chatsFound(found: Outcome<TdApi.Chats>): Outcome<ImmutableList<Chat>> =
    when (found) {
        is Outcome.Success -> Outcome.Success(chatsOf(found.value.chatIds.toList()))
        is Outcome.Failure -> found
    }

/** The chats with [ids], in their order; one that cannot be read (deleted meanwhile, a timeout) is left out. */
private suspend fun ChatRepository.chatsOf(ids: List<Long>): ImmutableList<Chat> =
    ids.distinct().mapNotNull { getChat(it).getOrNull() }.toImmutableList()

/** The messages of [found] with their chats; a message whose chat cannot be read is left out. */
private suspend fun ChatRepository.foundMessages(found: TdApi.FoundMessages): FoundMessages {
    val chats = HashMap<Long, Chat?>()
    val messages =
        found.messages
            .filterNotNull()
            .distinctBy { it.chatId to it.id }
            .mapNotNull { message ->
                val chatId = message.chatId
                if (chatId !in chats) chats[chatId] = getChat(chatId).getOrNull()
                chats[chatId]?.let { FoundMessage(message.toMessage(), it) }
            }
    return FoundMessages(
        messages = messages.toImmutableList(),
        nextOffset = found.nextOffset.ifEmpty { null },
        totalCount = found.totalCount.takeIf { it >= 0 },
    )
}

/** Chats per search of chats, on the device and on the server each, and contacts per search. */
private const val CHAT_LIMIT = 30
private const val CONTACT_LIMIT = 30

/** Messages per page of [TdlibSearchRepository.searchMessages]; TDLib allows up to 100, and may return fewer. */
private const val MESSAGE_PAGE_SIZE = 30

private const val BAD_REQUEST = 400

/** What TDLib could not search (a 400, see [TdlibSearchRepository]) found [nothing] instead of failing. */
private inline fun <T> Outcome<T>.orNothing(nothing: () -> T): Outcome<T> {
    val isBadRequest = this is Outcome.Failure && (error as? DomainError.Unknown)?.code == BAD_REQUEST
    return if (isBadRequest) Outcome.Success(nothing()) else this
}

private fun noChats() = TdApi.Chats(0, LongArray(0))

private fun noUsers() = TdApi.Users(0, LongArray(0))

private fun noMessages() = TdApi.FoundMessages(0, emptyArray(), "")
