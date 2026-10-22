package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibSearchRepository] on a [FakeTdlibClient]: the requests of each search, the chats they map to (through the real
 * chat repository, which reads unknown chats with getChat), the merge of local and server chats, message paging by
 * TDLib's offset, the recently found chats, and errors.
 */
class TdlibSearchRepositoryTest {
    private val client =
        FakeTdlibClient().apply {
            // Every chat exists, named after its id, except UNKNOWN_CHAT.
            on(TdApi.GetChat::class) { request ->
                if (request.chatId == UNKNOWN_CHAT) {
                    failure(400, "Chat not found")
                } else {
                    TdResult.Success(chat(id = request.chatId, title = "Chat ${request.chatId}"))
                }
            }
        }

    private fun TestScope.repository(): TdlibSearchRepository {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        return TdlibSearchRepository(client, chats)
    }

    private fun failure(
        code: Int,
        message: String,
    ) = TdResult.Failure(TdError(code, message))

    private fun chats(vararg ids: Long) = TdResult.Success(TdApi.Chats(ids.size, ids))

    private fun Outcome<ImmutableList<Chat>>.ids() = (this as Outcome.Success).value.map { it.id }

    private fun Outcome<FoundMessages>.page() = (this as Outcome.Success).value

    // A blank query

    @Test
    fun `a blank query finds nothing, without asking TDLib`() =
        runTest {
            val repository = repository()

            assertEquals(listOf(emptyList<Long>()), repository.searchChats("  ").toList().map { it.ids() })
            assertEquals(emptyList<Long>(), repository.searchContacts("").ids())
            assertEquals(emptyList<Long>(), repository.searchPublicChats(" ").ids())
            assertTrue(
                repository
                    .searchMessages("", offset = null)
                    .page()
                    .messages
                    .isEmpty(),
            )
            assertTrue(client.sent.isEmpty())
        }

    // Chats

    @Test
    fun `chats on the device come first, then the server's after them without duplicates`() =
        runTest {
            client.on(TdApi.SearchChats::class, chats(1, 2))
            client.on(TdApi.SearchChatsOnServer::class, chats(2, 3))

            val found = repository().searchChats(" ada ").toList()

            assertEquals(listOf(listOf(1L, 2L), listOf(1L, 2L, 3L)), found.map { it.ids() })
            assertEquals("Chat 1", (found.last() as Outcome.Success).value.first().title)
            val local = client.sentOf<TdApi.SearchChats>().single()
            assertEquals("ada", local.query)
            assertNull(local.typeFilter)
            assertEquals(30, local.limit)
            assertEquals("ada", client.sentOf<TdApi.SearchChatsOnServer>().single().query)
        }

    @Test
    fun `chats on the device show while the server is still searching`() =
        runTest {
            val server = CompletableDeferred<TdResult<TdApi.Chats>>()
            client.on(TdApi.SearchChats::class, chats(1))
            client.on(TdApi.SearchChatsOnServer::class) { server.await() }
            val found = mutableListOf<Outcome<ImmutableList<Chat>>>()

            backgroundScope.launch { repository().searchChats("ada").toList(found) }
            runCurrent()

            assertEquals(listOf(listOf(1L)), found.map { it.ids() })
            server.complete(chats(4))
            runCurrent()
            assertEquals(listOf(listOf(1L), listOf(1L, 4L)), found.map { it.ids() })
        }

    @Test
    fun `a server that fails leaves the chats on the device`() =
        runTest {
            client.on(TdApi.SearchChats::class, chats(1))
            client.on(TdApi.SearchChatsOnServer::class, failure(500, "Network is unreachable"))

            assertEquals(listOf(listOf(1L)), repository().searchChats("ada").toList().map { it.ids() })
        }

    @Test
    fun `without chats on the device, the server's failure comes instead`() =
        runTest {
            client.on(TdApi.SearchChats::class, chats())
            client.on(TdApi.SearchChatsOnServer::class, failure(429, "Too Many Requests: retry after 7"))

            val found = repository().searchChats("ada").toList()

            assertEquals(listOf(Outcome.Success(emptyList<Chat>()), Outcome.Failure(DomainError.FloodWait(7))), found)
        }

    @Test
    fun `a chat that cannot be read is left out`() =
        runTest {
            client.on(TdApi.SearchChats::class, chats(1, UNKNOWN_CHAT, 2))
            client.on(TdApi.SearchChatsOnServer::class, chats())

            assertEquals(
                listOf(1L, 2L),
                repository()
                    .searchChats("ada")
                    .toList()
                    .last()
                    .ids(),
            )
        }

    // Contacts

    @Test
    fun `contacts are their private chats, created on the device`() =
        runTest {
            client.on(TdApi.SearchContacts::class) { TdResult.Success(TdApi.Users(2, longArrayOf(7, 8))) }
            client.on(TdApi.CreatePrivateChat::class) { request ->
                TdResult.Success(chat(id = request.userId, type = TdApi.ChatTypePrivate(request.userId)))
            }

            val found = repository().searchContacts("gr")

            assertEquals(listOf(7L, 8L), found.ids())
            val search = client.sentOf<TdApi.SearchContacts>().single()
            assertEquals("gr", search.query)
            assertEquals(30, search.limit)
            assertEquals(listOf(7L, 8L), client.sentOf<TdApi.CreatePrivateChat>().map { it.userId })
            assertFalse(client.sentOf<TdApi.CreatePrivateChat>().any { it.force })
        }

    @Test
    fun `a failed search of contacts reports its error`() =
        runTest {
            client.on(TdApi.SearchContacts::class, failure(429, "Too Many Requests: retry after 5"))

            assertEquals(Outcome.Failure(DomainError.FloodWait(5)), repository().searchContacts("gr"))
        }

    @Test
    fun `all contacts are their private chats, sorted by name, without the ones that cannot be read`() =
        runTest {
            client.on(TdApi.GetContacts::class) { TdResult.Success(TdApi.Users(4, longArrayOf(7, 8, 9, UNKNOWN_CHAT))) }
            client.on(TdApi.CreatePrivateChat::class) { request ->
                TdResult.Success(chat(id = request.userId, type = TdApi.ChatTypePrivate(request.userId)))
            }
            val names = mapOf(7L to "Grace", 8L to "ada", 9L to "Bea")
            client.on(TdApi.GetChat::class) { request ->
                names[request.chatId]?.let { TdResult.Success(chat(id = request.chatId, title = it)) }
                    ?: failure(400, "Chat not found")
            }

            val contacts = repository().contacts()

            assertEquals(listOf(8L, 9L, 7L), contacts.ids())
            assertEquals(
                listOf(7L, 8L, 9L, UNKNOWN_CHAT),
                client.sentOf<TdApi.CreatePrivateChat>().map { it.userId }.sorted(),
            )
        }

    @Test
    fun `failing to read the contacts reports its error`() =
        runTest {
            client.on(TdApi.GetContacts::class, failure(429, "Too Many Requests: retry after 3"))

            assertEquals(Outcome.Failure(DomainError.FloodWait(3)), repository().contacts())
        }

    // Public chats

    @Test
    fun `public chats are the chats TDLib finds by username`() =
        runTest {
            client.on(TdApi.SearchPublicChats::class, chats(30, 31))

            assertEquals(listOf(30L, 31L), repository().searchPublicChats("@news").ids())
            val search = client.sentOf<TdApi.SearchPublicChats>().single()
            assertEquals("@news", search.query)
            assertNull(search.typeFilter)
        }

    @Test
    fun `a query too short for a username finds no public chats instead of failing`() =
        runTest {
            client.on(TdApi.SearchPublicChats::class, failure(400, "USERNAME_INVALID"))

            assertEquals(emptyList<Long>(), repository().searchPublicChats("ab").ids())
        }

    @Test
    fun `public chats fail without an answer in time`() =
        runTest {
            client.on(TdApi.SearchPublicChats::class) { CompletableDeferred<TdResult<TdApi.Chats>>().await() }

            assertEquals(Outcome.Failure(DomainError.Network), repository().searchPublicChats("news"))
        }

    // Messages

    @Test
    fun `the first page of messages in all chats, each with its chat, and the next page's offset`() =
        runTest {
            client.on(TdApi.SearchMessages::class) {
                TdResult.Success(
                    TdApi.FoundMessages(57, arrayOf(message(id = 20, chatId = 2), message(id = 10, chatId = 1)), "o2"),
                )
            }

            val page = repository().searchMessages(" hello ", offset = null).page()

            assertEquals(listOf(2L to 20L, 1L to 10L), page.messages.map { it.message.chatId to it.message.id })
            assertEquals(listOf("Chat 2", "Chat 1"), page.messages.map { it.chat.title })
            assertEquals("o2", page.nextOffset)
            assertEquals(57, page.totalCount)
            val search = client.sentOf<TdApi.SearchMessages>().single()
            assertNull(search.chatList)
            assertEquals("hello", search.query)
            assertEquals("", search.offset)
            assertEquals(30, search.limit)
            assertNull(search.filter)
            assertNull(search.chatTypeFilter)
        }

    @Test
    fun `the next page is asked from the previous page's offset, and an empty offset ends the search`() =
        runTest {
            client.on(TdApi.SearchMessages::class) {
                TdResult.Success(TdApi.FoundMessages(-1, arrayOf(message(id = 5, chatId = 1)), ""))
            }

            val page = repository().searchMessages("hello", offset = "o2").page()

            assertEquals("o2", client.sentOf<TdApi.SearchMessages>().single().offset)
            assertNull(page.nextOffset)
            assertNull(page.totalCount)
        }

    @Test
    fun `messages whose chat cannot be read, and repeated ones, are left out`() =
        runTest {
            client.on(TdApi.SearchMessages::class) {
                val unknownChat = message(id = 3, chatId = UNKNOWN_CHAT)
                val messages = arrayOf(unknownChat, message(id = 2, chatId = 1), message(id = 2, chatId = 1))
                TdResult.Success(TdApi.FoundMessages(3, messages, "o"))
            }

            val page = repository().searchMessages("hello", offset = null).page()

            assertEquals(listOf(2L), page.messages.map { it.message.id })
            // Each chat is read once per page.
            assertEquals(1, client.sentOf<TdApi.GetChat>().count { it.chatId == UNKNOWN_CHAT })
        }

    @Test
    fun `photos and videos, and links, are searched with TDLib's filters`() =
        runTest {
            client.on(TdApi.SearchMessages::class) {
                TdResult.Success(TdApi.FoundMessages(1, arrayOf(message(id = 7, chatId = 1)), ""))
            }
            val repository = repository()

            val media = repository.searchMessages("cat", offset = null, MessageSearchFilter.PhotoAndVideo).page()
            repository.searchMessages("cat", offset = null, MessageSearchFilter.Url)

            assertEquals(listOf(7L), media.messages.map { it.message.id })
            val (photos, links) = client.sentOf<TdApi.SearchMessages>()
            assertTrue(photos.filter is TdApi.SearchMessagesFilterPhotoAndVideo)
            assertEquals("cat", photos.query)
            assertTrue(links.filter is TdApi.SearchMessagesFilterUrl)
        }

    @Test
    fun `a blank query finds every photo and video, or every link, but no messages without a filter`() =
        runTest {
            client.on(TdApi.SearchMessages::class) {
                TdResult.Success(TdApi.FoundMessages(1, arrayOf(message(id = 7, chatId = 1)), "o"))
            }
            val repository = repository()

            val media = repository.searchMessages(" ", offset = null, MessageSearchFilter.PhotoAndVideo).page()
            val links = repository.searchMessages("", offset = "o", MessageSearchFilter.Url).page()
            val all = repository.searchMessages("", offset = null, MessageSearchFilter.All).page()

            assertEquals(listOf(7L), media.messages.map { it.message.id })
            assertEquals(listOf(7L), links.messages.map { it.message.id })
            assertTrue(all.messages.isEmpty())
            assertEquals(listOf("", ""), client.sentOf<TdApi.SearchMessages>().map { it.query })
            assertEquals("o", client.sentOf<TdApi.SearchMessages>().last().offset)
        }

    @Test
    fun `a failed page reports its error`() =
        runTest {
            client.on(TdApi.SearchMessages::class, failure(429, "Too Many Requests: retry after 3"))

            assertEquals(Outcome.Failure(DomainError.FloodWait(3)), repository().searchMessages("hello", offset = null))
        }

    // Recently found chats

    @Test
    fun `recent chats are TDLib's whole list of recently found chats, in its order`() =
        runTest {
            client.on(TdApi.SearchRecentlyFoundChats::class, chats(5, 4))

            assertEquals(listOf(5L, 4L), repository().recentChats().ids())
            val request = client.sentOf<TdApi.SearchRecentlyFoundChats>().single()
            assertEquals("", request.query)
            assertEquals(SearchRepository.RECENT_LIMIT, request.limit)
        }

    @Test
    fun `adding, removing and clearing recent chats send their requests`() =
        runTest {
            val ok = TdResult.Success(TdApi.Ok())
            client.on(TdApi.AddRecentlyFoundChat::class, ok)
            client.on(TdApi.RemoveRecentlyFoundChat::class, ok)
            client.on(TdApi.ClearRecentlyFoundChats::class, ok)
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.addRecentChat(5))
            assertEquals(Outcome.Success(Unit), repository.removeRecentChat(4))
            assertEquals(Outcome.Success(Unit), repository.clearRecentChats())

            assertEquals(5L, client.sentOf<TdApi.AddRecentlyFoundChat>().single().chatId)
            assertEquals(4L, client.sentOf<TdApi.RemoveRecentlyFoundChat>().single().chatId)
            assertEquals(1, client.sentOf<TdApi.ClearRecentlyFoundChats>().size)
        }

    @Test
    fun `adding a recent chat TDLib does not know fails`() =
        runTest {
            client.on(TdApi.AddRecentlyFoundChat::class, failure(400, "Chat not found"))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().addRecentChat(UNKNOWN_CHAT))
        }

    // Top people

    @Test
    fun `top people are TDLib's top users`() =
        runTest {
            client.on(TdApi.GetTopChats::class, chats(9, 8))

            assertEquals(listOf(9L, 8L), repository().topPeople().ids())
            val request = client.sentOf<TdApi.GetTopChats>().single()
            assertTrue(request.category is TdApi.TopChatCategoryUsers)
            assertEquals(SearchRepository.TOP_PEOPLE_LIMIT, request.limit)
        }

    @Test
    fun `top people are none while their ranking is turned off`() =
        runTest {
            client.on(TdApi.GetTopChats::class, failure(400, "Top chat computation is disabled"))

            assertEquals(emptyList<Long>(), repository().topPeople().ids())
        }

    // A username

    @Test
    fun `a username of a user is theirs, with their private chat`() =
        runTest {
            client.on(TdApi.SearchPublicChat::class, TdResult.Success(chat(id = 42, type = TdApi.ChatTypePrivate(42))))

            assertEquals(Outcome.Success(UsernameOwner.User(userId = 42, chatId = 42)), repository().publicChat("@ada"))
            assertEquals("ada", client.sentOf<TdApi.SearchPublicChat>().single().username)
        }

    @Test
    fun `a username of a group or channel is the chat's`() =
        runTest {
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = -100, type = TdApi.ChatTypeSupergroup(5, true))),
            )

            assertEquals(Outcome.Success(UsernameOwner.Chat(-100)), repository().publicChat("news"))
        }

    @Test
    fun `a username nobody has is not found`() =
        runTest {
            client.on(TdApi.SearchPublicChat::class, failure(400, "USERNAME_NOT_OCCUPIED"))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository().publicChat("nobody"))

            client.on(TdApi.SearchPublicChat::class, failure(400, "USERNAME_INVALID"))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository().publicChat("x"))
        }

    @Test
    fun `a blank username is not found, without asking TDLib`() =
        runTest {
            assertEquals(Outcome.Failure(DomainError.NotFound), repository().publicChat(" @"))
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `a username lookup keeps other errors`() =
        runTest {
            client.on(TdApi.SearchPublicChat::class, failure(429, "Too Many Requests: retry after 5"))

            assertEquals(Outcome.Failure(DomainError.FloodWait(5)), repository().publicChat("ada"))
        }

    private companion object {
        const val UNKNOWN_CHAT = 404L
    }
}
