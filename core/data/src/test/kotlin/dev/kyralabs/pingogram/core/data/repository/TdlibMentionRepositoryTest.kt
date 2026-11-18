package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibMentionRepository] on a [FakeTdlibClient]: which chats it searches and with which filter, the merge of their
 * mentions and replies (unread first, newest first, once each), and failures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibMentionRepositoryTest {
    /** The chats TDLib knows, by id. */
    private val chats = mutableMapOf<Long, TdApi.Chat>()

    /** The main list's and the archive's chat ids. */
    private var main: TdResult<TdApi.Chats> = chatIds()
    private var archive: TdResult<TdApi.Chats> = chatIds()

    /** What each chat's search finds, by chat id. */
    private val found = mutableMapOf<Long, TdResult<TdApi.FoundChatMessages>>()

    private val client =
        FakeTdlibClient().apply {
            on(TdApi.GetChat::class) { request ->
                chats[request.chatId]?.let { TdResult.Success(it) } ?: TdResult.Failure(TdError(400, "Chat not found"))
            }
            on(TdApi.GetChats::class) { request -> if (request.chatList is TdApi.ChatListMain) main else archive }
            on(TdApi.SearchChatMessages::class) { request ->
                found[request.chatId] ?: TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0))
            }
        }

    private fun TestScope.repository() =
        TdlibMentionRepository(
            client,
            TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats()),
        )

    private fun group(
        id: Long,
        unreadMentions: Int = 0,
    ) {
        chats[id] =
            chat(id = id, type = TdApi.ChatTypeBasicGroup(id), title = "Group $id", unreadMentionCount = unreadMentions)
    }

    private fun channel(id: Long) {
        chats[id] = chat(id = id, type = TdApi.ChatTypeSupergroup(id, true), title = "Channel $id")
    }

    private fun chatIds(vararg ids: Long) = TdResult.Success(TdApi.Chats(ids.size, ids))

    private fun messages(vararg messages: TdApi.Message) =
        TdResult.Success(TdApi.FoundChatMessages(messages.size, arrayOf(*messages), 0))

    private fun Outcome<ImmutableList<FoundMessage>>.ids() =
        (this as Outcome.Success).value.map { it.chat.id to it.message.id }

    @Test
    fun `chats with unread mentions are searched for them, groups for their newest, channels not at all`() =
        runTest {
            group(1, unreadMentions = 2)
            group(2)
            channel(3)
            main = chatIds(1, 2, 3)

            repository().mentions()

            val searches = client.sentOf<TdApi.SearchChatMessages>().map { it.chatId to it.filter::class }
            assertEquals(
                setOf(
                    1L to TdApi.SearchMessagesFilterUnreadMention::class,
                    1L to TdApi.SearchMessagesFilterMention::class,
                    2L to TdApi.SearchMessagesFilterMention::class,
                ),
                searches.toSet(),
            )
        }

    @Test
    fun `archived chats are searched for unread mentions only`() =
        runTest {
            group(1, unreadMentions = 1)
            group(2)
            archive = chatIds(1, 2)

            repository().mentions()

            assertEquals(
                listOf(1L to TdApi.SearchMessagesFilterUnreadMention::class),
                client.sentOf<TdApi.SearchChatMessages>().map { it.chatId to it.filter::class },
            )
        }

    @Test
    fun `mentions come unread first, then newest first, each once`() =
        runTest {
            group(1, unreadMentions = 1)
            group(2)
            main = chatIds(1, 2)
            val unread = message(id = 10, chatId = 1, date = 100, containsUnreadMention = true)
            found[1] = messages(unread, message(id = 11, chatId = 1, date = 300))
            found[2] = messages(message(id = 20, chatId = 2, date = 200))

            val result = repository().mentions()

            assertEquals(listOf(1L to 10L, 1L to 11L, 2L to 20L), result.ids())
            assertTrue(
                (result as Outcome.Success)
                    .value
                    .first()
                    .message.containsUnreadMention,
            )
        }

    @Test
    fun `without groups or unread mentions nothing is searched`() =
        runTest {
            channel(3)
            main = chatIds(3)

            assertEquals(emptyList<Pair<Long, Long>>(), repository().mentions().ids())
            assertTrue(client.sentOf<TdApi.SearchChatMessages>().isEmpty())
        }

    @Test
    fun `a chat that cannot be searched is left out`() =
        runTest {
            group(1)
            group(2)
            main = chatIds(1, 2)
            found[1] = TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED"))
            found[2] = messages(message(id = 20, chatId = 2))

            assertEquals(listOf(2L to 20L), repository().mentions().ids())
        }

    @Test
    fun `it fails when the chat list or every search fails`() =
        runTest {
            main = TdResult.Failure(TdError(500, "Request aborted"))
            assertTrue(repository().mentions() is Outcome.Failure)

            group(1)
            main = chatIds(1)
            found[1] = TdResult.Failure(TdError(429, "Too Many Requests: retry after 30"))
            val failure = repository().mentions() as Outcome.Failure
            assertTrue(failure.error is DomainError.FloodWait)
        }
}
