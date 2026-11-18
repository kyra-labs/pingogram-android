package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.MentionRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [MentionRepository] on TDLib, without a cache. td_api.tl's `searchMessages`, the search across chats, does not take
 * the mention filters, so the messages come chat by chat from `searchChatMessages`, whose `searchMessagesFilterMention`
 * finds the mentions of the user and the replies to the user's messages alike:
 *
 * - **Unread**: every chat of the main list and the archive (the first [LISTED_CHATS] of each, as `getChats` gives
 *   them) whose `unread_mention_count` is not 0, with `searchMessagesFilterUnreadMention`; at most [UNREAD_CHATS]
 *   chats.
 * - **Recent**: the first [RECENT_GROUPS] groups of the main list, the most recently active, with
 *   `searchMessagesFilterMention`, [RECENT_PER_CHAT] each: only groups mention or reply, and a page per group keeps
 *   the requests few.
 *
 * At most [PARALLEL_SEARCHES] searches run at once. The messages are merged (one per message), unread first, then
 * newest first, at most [MAX_MESSAGES].
 */
@AccountScoped
class TdlibMentionRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val chatRepository: ChatRepository,
    ) : MentionRepository {
        override suspend fun mentions(): Outcome<ImmutableList<FoundMessage>> =
            when (val searches = searches()) {
                is Outcome.Success -> found(searches.value)
                is Outcome.Failure -> searches
            }

        /** The searches to run: the unread mentions of the chats with any, the newest of the most recent groups. */
        private suspend fun searches(): Outcome<List<TdApi.SearchChatMessages>> {
            val main = chatsOf(TdApi.ChatListMain())
            if (main !is Outcome.Success) return main as Outcome.Failure
            val archived = chatsOf(TdApi.ChatListArchive()).getOrNull().orEmpty()
            val unread = (main.value + archived).filter { it.unreadMentionCount > 0 }.take(UNREAD_CHATS)
            val groups = main.value.filter { it.isGroup }.take(RECENT_GROUPS)
            return Outcome.Success(unread.map { it.id.unread() } + groups.map { it.id.recent() })
        }

        /** What [searches] find, merged; the first failure when every one fails. */
        private suspend fun found(searches: List<TdApi.SearchChatMessages>): Outcome<ImmutableList<FoundMessage>> {
            val gate = Semaphore(PARALLEL_SEARCHES)
            val results =
                coroutineScope {
                    searches.map { search -> async { gate.withPermit { client.request(search) } } }.awaitAll()
                }
            val found = results.mapNotNull { (it as? Outcome.Success)?.value }
            if (found.isEmpty() && results.isNotEmpty()) return results.first() as Outcome.Failure
            val messages = found.flatMap { it.messages.filterNotNull() }.toTypedArray()
            val mapped = chatRepository.foundMessages(TdApi.FoundMessages(-1, messages, "")).messages
            return Outcome.Success(mapped.sortedWith(NEWEST_UNREAD_FIRST).take(MAX_MESSAGES).toImmutableList())
        }

        /** The first [LISTED_CHATS] chats of [list], in its order; a chat that cannot be read is left out. */
        private suspend fun chatsOf(list: TdApi.ChatList): Outcome<List<Chat>> =
            client.request(TdApi.GetChats(list, LISTED_CHATS)).map { chats ->
                chats.chatIds.distinct().mapNotNull { chatRepository.getChat(it).getOrNull() }
            }

        private companion object {
            const val LISTED_CHATS = 100
            const val UNREAD_CHATS = 30
            const val UNREAD_PER_CHAT = 20
            const val RECENT_GROUPS = 20
            const val RECENT_PER_CHAT = 10
            const val PARALLEL_SEARCHES = 4
            const val MAX_MESSAGES = 200

            val NEWEST_UNREAD_FIRST =
                compareByDescending<FoundMessage> { it.message.containsUnreadMention }
                    .thenByDescending { it.message.date }
                    .thenByDescending { it.message.id }

            /** This chat's unread mentions and replies, the newest first. */
            fun Long.unread() = search(this, UNREAD_PER_CHAT, TdApi.SearchMessagesFilterUnreadMention())

            /** This chat's newest mentions and replies, read or not. */
            fun Long.recent() = search(this, RECENT_PER_CHAT, TdApi.SearchMessagesFilterMention())

            /** The newest [limit] messages of chat [chatId] that [filter] finds, in the whole chat. */
            fun search(
                chatId: Long,
                limit: Int,
                filter: TdApi.SearchMessagesFilter,
            ) = TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, limit, filter)
        }
    }

/** A group, whose members can mention the user or reply to them; a channel's posts do neither. */
private val Chat.isGroup: Boolean
    get() =
        when (val type = type) {
            is ChatType.BasicGroup -> true
            is ChatType.Supergroup -> !type.isChannel
            else -> false
        }
