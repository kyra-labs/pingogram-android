package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddedReaction
import dev.kyralabs.pingogram.core.domain.model.AddedReactionsPage
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageViewer
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibMessageInteractionRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibMessageInteractionRepository(client)

    @Nested
    inner class Viewers {
        @Test
        fun `the viewers come with their view dates, the latest view first`() =
            runTest {
                client.on(
                    TdApi.GetMessageViewers::class,
                    TdResult.Success(
                        TdApi.MessageViewers(
                            arrayOf(
                                TdApi.MessageViewer(7, 100),
                                TdApi.MessageViewer(8, 300),
                                TdApi.MessageViewer(9, 200),
                            ),
                        ),
                    ),
                )

                assertEquals(
                    Outcome.Success(
                        persistentListOf(MessageViewer(8, 300), MessageViewer(9, 200), MessageViewer(7, 100)),
                    ),
                    repository.viewers(CHAT, MESSAGE),
                )
                val request = client.sentOf<TdApi.GetMessageViewers>().single()
                assertEquals(CHAT, request.chatId)
                assertEquals(MESSAGE, request.messageId)
            }

        @Test
        fun `a message nobody saw has no viewers`() =
            runTest {
                client.on(TdApi.GetMessageViewers::class, TdResult.Success(TdApi.MessageViewers(arrayOf())))

                assertEquals(Outcome.Success(persistentListOf<MessageViewer>()), repository.viewers(CHAT, MESSAGE))
            }

        @Test
        fun `a message too old for its viewers says so`() =
            runTest {
                client.on(TdApi.GetMessageViewers::class, TdResult.Failure(TdError(400, "MSG_TOO_OLD")))

                assertEquals(Outcome.Failure(DomainError.MessageViewersUnavailable), repository.viewers(CHAT, MESSAGE))
            }

        @Test
        fun `a group too large for its viewers says so`() =
            runTest {
                client.on(TdApi.GetMessageViewers::class, TdResult.Failure(TdError(400, "CHAT_TOO_BIG")))

                assertEquals(Outcome.Failure(DomainError.MessageViewersUnavailable), repository.viewers(CHAT, MESSAGE))
            }

        @Test
        fun `a flood wait is mapped`() =
            runTest {
                client.on(
                    TdApi.GetMessageViewers::class,
                    TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")),
                )

                assertEquals(Outcome.Failure(DomainError.FloodWait(9)), repository.viewers(CHAT, MESSAGE))
            }

        @Test
        fun `no answer in time is a network error`() =
            runTest {
                client.on(
                    TdApi.GetMessageViewers::class,
                ) { CompletableDeferred<TdResult<TdApi.MessageViewers>>().await() }

                val result = async { repository.viewers(CHAT, MESSAGE) }
                advanceTimeBy(REQUEST_TIMEOUT)
                runCurrent()

                assertEquals(Outcome.Failure(DomainError.Network), result.await())
            }
    }

    @Nested
    inner class Reactions {
        private fun answer(
            nextOffset: String,
            vararg reactions: TdApi.AddedReaction,
        ) = client.on(
            TdApi.GetMessageAddedReactions::class,
            TdResult.Success(TdApi.AddedReactions(reactions.size + 10, arrayOf(*reactions), nextOffset)),
        )

        @Test
        fun `a page holds the reactions with their senders, and where the next one starts`() =
            runTest {
                answer(
                    "next",
                    TdApi.AddedReaction(TdApi.ReactionTypeEmoji("❤"), TdApi.MessageSenderUser(7), false, 50),
                    TdApi.AddedReaction(TdApi.ReactionTypeCustomEmoji(5), TdApi.MessageSenderChat(-100), true, 40),
                )

                assertEquals(
                    Outcome.Success(
                        AddedReactionsPage(
                            totalCount = 12,
                            reactions =
                                persistentListOf(
                                    AddedReaction(ReactionType.Emoji("❤"), MessageSender.User(7), false, 50),
                                    AddedReaction(ReactionType.CustomEmoji(5), MessageSender.Chat(-100), true, 40),
                                ),
                            nextOffset = "next",
                        ),
                    ),
                    repository.addedReactions(CHAT, MESSAGE, type = null),
                )
            }

        @Test
        fun `every reaction is asked for without a type, from the start`() =
            runTest {
                answer("")

                repository.addedReactions(CHAT, MESSAGE, type = null)

                val request = client.sentOf<TdApi.GetMessageAddedReactions>().single()
                assertEquals(CHAT, request.chatId)
                assertEquals(MESSAGE, request.messageId)
                assertNull(request.reactionType)
                assertEquals("", request.offset)
                assertEquals(50, request.limit)
            }

        @Test
        fun `one reaction's page is asked for by its type and offset`() =
            runTest {
                answer("")

                repository.addedReactions(CHAT, MESSAGE, ReactionType.CustomEmoji(5), offset = "abc", limit = 20)
                repository.addedReactions(CHAT, MESSAGE, ReactionType.Emoji("👍"))
                repository.addedReactions(CHAT, MESSAGE, ReactionType.Paid)

                val (custom, emoji, paid) = client.sentOf<TdApi.GetMessageAddedReactions>()
                assertEquals(5L, (custom.reactionType as TdApi.ReactionTypeCustomEmoji).customEmojiId)
                assertEquals("abc", custom.offset)
                assertEquals(20, custom.limit)
                assertEquals("👍", (emoji.reactionType as TdApi.ReactionTypeEmoji).emoji)
                assertInstanceOf(TdApi.ReactionTypePaid::class.java, paid.reactionType)
            }

        @Test
        fun `the last page has no next offset`() =
            runTest {
                answer("", TdApi.AddedReaction(TdApi.ReactionTypeEmoji("❤"), TdApi.MessageSenderUser(7), false, 50))

                val page = (repository.addedReactions(CHAT, MESSAGE, type = null) as Outcome.Success).value

                assertTrue(page.isLast)
            }

        @Test
        fun `TDLib's limit caps a page`() =
            runTest {
                answer("")

                repository.addedReactions(CHAT, MESSAGE, type = null, limit = 500)

                assertEquals(100, client.sentOf<TdApi.GetMessageAddedReactions>().single().limit)
            }

        @Test
        fun `errors are mapped`() =
            runTest {
                client.on(
                    TdApi.GetMessageAddedReactions::class,
                    TdResult.Failure(TdError(500, "Network is unreachable")),
                )

                assertEquals(Outcome.Failure(DomainError.Network), repository.addedReactions(CHAT, MESSAGE, null))
            }
    }

    private companion object {
        const val CHAT = -42L
        const val MESSAGE = 1024L
    }
}
