package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toMessage
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.drinkless.tdlib.TdApi
import java.text.Collator
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
 *   contact. All contacts: `getContacts`, their chats asked for all at once, then sorted by name in the user's
 *   language.
 * - **Public chats**: `searchPublicChats`; TDLib leaves out the user's chats and contacts. A typed `@username` or
 *   `t.me/username` link is searched as the username it names ([publicChatQuery], `REQUIREMENTS.md` FR-P5).
 * - **A username**: `searchPublicChat`, for a mention tapped in a message ([publicChat]).
 * - **Messages**: `searchMessages` in every chat list (`chat_list` null, so the archive too), paged by TDLib's offset,
 *   with `searchMessagesFilterPhotoAndVideo` or `searchMessagesFilterUrl` for photos and videos or links, which TDLib
 *   also searches without a query.
 * - **Recently found chats**: `searchRecentlyFoundChats` with an empty query, which returns the whole list (TDLib has
 *   no `getRecentlyFoundChats`), and `add`/`remove`/`clearRecentlyFoundChats`.
 * - **Top people**: `getTopChats(topChatCategoryUsers)`.
 *
 * Every query is trimmed. TDLib answers 400 for what it cannot search (a username prefix too short to resolve, top
 * chats while their ranking is off); that finds nothing instead of failing.
 */
@Suppress("TooManyFunctions") // One function per search of the interface, each a TDLib request or two.
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
                    .mapNotNull { chatRepository.privateChat(client, it) }
                    .toImmutableList()
            }
        }

        override suspend fun contacts(): Outcome<ImmutableList<Chat>> =
            client.request(TdApi.GetContacts()).map { users ->
                val chats =
                    coroutineScope {
                        users.userIds
                            .distinct()
                            .map { async { chatRepository.privateChat(client, it) } }
                            .awaitAll()
                    }
                val collator = Collator.getInstance()
                chats.filterNotNull().sortedWith { a, b -> collator.compare(a.title, b.title) }.toImmutableList()
            }

        override suspend fun searchPublicChats(query: String): Outcome<ImmutableList<Chat>> {
            val text = publicChatQuery(query)
            if (text.isEmpty()) return Outcome.Success(persistentListOf())
            return chatRepository.chatsFound(client.request(TdApi.SearchPublicChats(text, null)).orNothing(::noChats))
        }

        /**
         * `searchPublicChat`, which TDLib answers from memory for a username it resolved before. TDLib announces the
         * chat before it answers; its type says whose it is. A username nobody has is a 400 (`USERNAME_NOT_OCCUPIED`,
         * or `USERNAME_INVALID` for one that cannot be a username): [DomainError.NotFound].
         */
        override suspend fun publicChat(username: String): Outcome<UsernameOwner> {
            val name = username.trim().removePrefix("@")
            if (name.isEmpty()) return Outcome.Failure(DomainError.NotFound)
            return when (val found = client.request(TdApi.SearchPublicChat(name))) {
                is Outcome.Success -> {
                    val chat = found.value
                    when (val type = chat.type) {
                        is TdApi.ChatTypePrivate -> Outcome.Success(UsernameOwner.User(type.userId, chat.id))
                        else -> Outcome.Success(UsernameOwner.Chat(chat.id))
                    }
                }

                is Outcome.Failure -> {
                    val error = found.error
                    val isUnknown =
                        (error as? DomainError.Unknown)?.code == BAD_REQUEST || error == DomainError.UsernameInvalid
                    if (isUnknown) Outcome.Failure(DomainError.NotFound) else found
                }
            }
        }

        override suspend fun searchMessages(
            query: String,
            offset: String?,
            filter: MessageSearchFilter,
        ): Outcome<FoundMessages> {
            val text = query.trim()
            val tdFilter = filter.toTdApi()
            if (text.isEmpty() && tdFilter == null) {
                return Outcome.Success(FoundMessages(persistentListOf(), nextOffset = null, null))
            }
            val request =
                TdApi.SearchMessages(null, text, offset.orEmpty(), MESSAGE_PAGE_SIZE, tdFilter, null, 0, 0)
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

/** The private chat with user [userId], created on the device by [client] if needed; null if that fails. */
private suspend fun ChatRepository.privateChat(
    client: TdlibClient,
    userId: Long,
): Chat? {
    val created = client.request(TdApi.CreatePrivateChat(userId, false)).getOrNull() ?: return null
    return getChat(created.id).getOrNull()
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

/** TDLib's filter for [this]; none for every message. */
private fun MessageSearchFilter.toTdApi(): TdApi.SearchMessagesFilter? =
    when (this) {
        MessageSearchFilter.All -> null
        MessageSearchFilter.PhotoAndVideo -> TdApi.SearchMessagesFilterPhotoAndVideo()
        MessageSearchFilter.Url -> TdApi.SearchMessagesFilterUrl()
    }

/** Chats per search of chats, on the device and on the server each, and contacts per search. */
private const val CHAT_LIMIT = 30
private const val CONTACT_LIMIT = 30

/** Messages per page of [TdlibSearchRepository.searchMessages]; TDLib allows up to 100, and may return fewer. */
private const val MESSAGE_PAGE_SIZE = 30

private const val BAD_REQUEST = 400

/**
 * What TDLib could not search (a 400, see [TdlibSearchRepository]) found [nothing] instead of failing. That includes
 * `USERNAME_INVALID`, which `searchPublicChats` answers for a query too short for a username, and which the error
 * mapping names for editing the profile.
 */
private inline fun <T> Outcome<T>.orNothing(nothing: () -> T): Outcome<T> {
    val error = (this as? Outcome.Failure)?.error
    val isBadRequest = (error as? DomainError.Unknown)?.code == BAD_REQUEST || error == DomainError.UsernameInvalid
    return if (isBadRequest) Outcome.Success(nothing()) else this
}

private fun noChats() = TdApi.Chats(0, LongArray(0))

private fun noUsers() = TdApi.Users(0, LongArray(0))

private fun noMessages() = TdApi.FoundMessages(0, emptyArray(), "")

/**
 * What public chats [query] searches for: the username of a typed `@username` or of a `t.me/username` link
 * (`telegram.me`, `telegram.dog`, with or without `https://`), else the query itself, trimmed.
 */
internal fun publicChatQuery(query: String): String {
    val text = query.trim()
    USERNAME_LINK.matchEntire(text)?.let { return it.groupValues[1] }
    return text.removePrefix("@")
}

/** A link to a username: `t.me/username`, with or without the scheme, `telegram.me` and `telegram.dog` too. */
private val USERNAME_LINK =
    Regex(
        """(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me|telegram\.dog)/@?([A-Za-z0-9_]{4,32})/?""",
        RegexOption.IGNORE_CASE,
    )
