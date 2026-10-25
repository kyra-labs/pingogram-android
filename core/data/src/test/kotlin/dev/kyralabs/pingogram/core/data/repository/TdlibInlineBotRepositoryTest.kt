package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButtonType
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.animation
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.photo
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.sticker
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibInlineBotRepository] on a [FakeTdlibClient]: finding an inline bot by its username, its results with and
 * without a location, every kind of result, and sending one into a topic with a reply.
 */
class TdlibInlineBotRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibInlineBotRepository(client)

    private fun bot(
        isInline: Boolean = true,
        placeholder: String = "Search GIFs…",
        needLocation: Boolean = false,
    ) = TdApi.UserTypeBot().also {
        it.isInline = isInline
        it.inlineQueryPlaceholder = placeholder
        it.needLocation = needLocation
    }

    private fun publicChat(type: TdApi.ChatType = TdApi.ChatTypePrivate(BOT)) =
        client.on(TdApi.SearchPublicChat::class, TdResult.Success(chat(id = BOT_CHAT, type = type)))

    @Test
    fun `an inline bot's username finds the bot with its placeholder`() =
        runTest {
            publicChat()
            client.on(
                TdApi.GetUser::class,
                TdResult.Success(user(id = BOT, usernames = listOf("gif"), type = bot(needLocation = true))),
            )

            val found = repository.inlineBot("GIF")

            assertEquals(Outcome.Success(InlineBot(BOT, BOT_CHAT, "gif", "Search GIFs…", needsLocation = true)), found)
            assertEquals("GIF", client.sentOf<TdApi.SearchPublicChat>().single().username)
        }

    @Test
    fun `a bot that answers no inline queries is no inline bot`() =
        runTest {
            publicChat()
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = BOT, type = bot(isInline = false))))

            assertEquals(Outcome.Success(null), repository.inlineBot("helper_bot"))
        }

    @Test
    fun `a person or a group is no inline bot`() =
        runTest {
            publicChat()
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = BOT)))
            assertEquals(Outcome.Success(null), repository.inlineBot("ada"))

            publicChat(TdApi.ChatTypeSupergroup(7, false))
            assertEquals(Outcome.Success(null), repository.inlineBot("group"))
            assertEquals(1, client.sentOf<TdApi.GetUser>().size)
        }

    @Test
    fun `a username nobody has is no inline bot`() =
        runTest {
            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(400, "USERNAME_NOT_OCCUPIED")))
            assertEquals(Outcome.Success(null), repository.inlineBot("nobody"))

            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(400, "USERNAME_INVALID")))
            assertEquals(Outcome.Success(null), repository.inlineBot("a"))
        }

    @Test
    fun `an empty username asks nothing`() =
        runTest {
            assertEquals(Outcome.Success(null), repository.inlineBot("@"))
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `a lookup that fails otherwise is a failure`() =
        runTest {
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.inlineBot("gif"))
        }

    @Test
    fun `results come with the query, the offset and the location agreed to`() =
        runTest {
            val button = TdApi.InlineQueryResultsButton("Sign in", TdApi.InlineQueryResultsButtonTypeStartBot("login"))
            client.on(
                TdApi.GetInlineQueryResults::class,
                TdResult.Success(
                    TdApi.InlineQueryResults(
                        QUERY_ID,
                        button,
                        arrayOf(TdApi.InlineQueryResultAnimation("a1", animation(), "Cat")),
                        "next",
                    ),
                ),
            )

            val page = (repository.results(BOT, CHAT, "cats", "20", Location(52.5, 13.4)) as Outcome.Success).value

            assertEquals(QUERY_ID, page.queryId)
            assertEquals(InlineResultsButton("Sign in", InlineResultsButtonType.StartBot("login")), page.button)
            assertEquals("a1", page.results.single().id)
            assertEquals("Cat", (page.results.single() as InlineResult.Animation).title)
            assertEquals("next", page.nextOffset)
            val sent = client.sentOf<TdApi.GetInlineQueryResults>().single()
            assertEquals(BOT, sent.botUserId)
            assertEquals(CHAT, sent.chatId)
            assertEquals("cats", sent.query)
            assertEquals("20", sent.offset)
            assertEquals(52.5, sent.userLocation.latitude)
            assertEquals(13.4, sent.userLocation.longitude)
        }

    @Test
    fun `results without a location send none`() =
        runTest {
            client.on(
                TdApi.GetInlineQueryResults::class,
                TdResult.Success(TdApi.InlineQueryResults(QUERY_ID, null, emptyArray(), "")),
            )

            val page = (repository.results(BOT, CHAT, "") as Outcome.Success).value

            assertNull(client.sentOf<TdApi.GetInlineQueryResults>().single().userLocation)
            assertNull(page.button)
            assertEquals("", page.nextOffset)
        }

    @Test
    fun `every kind of result is mapped, stickers that cannot be shown are left out`() =
        runTest {
            val unknownSticker = sticker().also { it.format = null }
            val game = TdApi.Game(1, "chess", "Chess", null, "Play chess", photo(), null)
            val document = TdApi.Document("cats.pdf", "application/pdf", null, null, file(id = 5))
            val results =
                arrayOf(
                    TdApi.InlineQueryResultPhoto("p", photo(), "Photo", "A photo"),
                    TdApi.InlineQueryResultSticker("s", sticker()),
                    TdApi.InlineQueryResultSticker("s2", unknownSticker),
                    TdApi.InlineQueryResultArticle("ar", "https://example.org", "Cats", "All about cats", null),
                    TdApi.InlineQueryResultLocation("l", TdApi.Location(1.0, 2.0, 0.0), "Here", null),
                    TdApi.InlineQueryResultDocument("d", document, "Cats", "PDF"),
                    TdApi.InlineQueryResultGame("g", game),
                )
            client.on(
                TdApi.GetInlineQueryResults::class,
                TdResult.Success(
                    TdApi.InlineQueryResults(
                        QUERY_ID,
                        TdApi.InlineQueryResultsButton("Open", TdApi.InlineQueryResultsButtonTypeWebApp("https://x")),
                        results.map { it as TdApi.InlineQueryResult }.toTypedArray(),
                        "",
                    ),
                ),
            )

            val page = (repository.results(BOT, CHAT, "cats") as Outcome.Success).value

            assertEquals(listOf("p", "s", "ar", "l", "d", "g"), page.results.map { it.id })
            assertInstanceOf(InlineResult.Photo::class.java, page.results[0])
            assertInstanceOf(InlineResult.Sticker::class.java, page.results[1])
            val article = page.results[2] as InlineResult.Article
            assertEquals("https://example.org", article.url)
            assertEquals("All about cats", article.description)
            assertEquals(Location(1.0, 2.0), (page.results[3] as InlineResult.Location).location)
            assertEquals("cats.pdf", (page.results[4] as InlineResult.Document).document.fileName)
            assertEquals("Chess", (page.results[5] as InlineResult.Game).title)
            assertEquals(InlineResultsButtonType.WebApp, page.button?.type)
        }

    @Test
    fun `results that fail are a failure`() =
        runTest {
            client.on(
                TdApi.GetInlineQueryResults::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 12")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(12)), repository.results(BOT, CHAT, "cats"))
        }

    @Test
    fun `sending a result names the bot, replies and goes into the topic`() =
        runTest {
            client.on(TdApi.SendInlineQueryResultMessage::class, TdResult.Success(TdApi.Message()))

            val sent =
                repository.send(
                    CHAT,
                    QUERY_ID,
                    "a1",
                    SendOptions(silent = true, scheduledAt = 1_800_000_000, topic = MessageTopic.Forum(3)),
                    replyTo = ReplyTarget(77),
                )

            assertEquals(Outcome.Success(Unit), sent)
            val request = client.sentOf<TdApi.SendInlineQueryResultMessage>().single()
            assertEquals(CHAT, request.chatId)
            assertEquals(QUERY_ID, request.queryId)
            assertEquals("a1", request.resultId)
            assertFalse(request.hideViaBot)
            assertEquals(77, (request.replyTo as TdApi.InputMessageReplyToMessage).messageId)
            assertTrue(request.options.disableNotification)
            assertEquals(
                1_800_000_000,
                (request.options.schedulingState as TdApi.MessageSchedulingStateSendAtDate).sendDate,
            )
            assertEquals(3, (request.topicId as TdApi.MessageTopicForum).forumTopicId)
        }

    @Test
    fun `a result sent without options goes to the chat itself`() =
        runTest {
            client.on(TdApi.SendInlineQueryResultMessage::class, TdResult.Success(TdApi.Message()))

            repository.send(CHAT, QUERY_ID, "a1")

            val request = client.sentOf<TdApi.SendInlineQueryResultMessage>().single()
            assertNull(request.topicId)
            assertNull(request.replyTo)
            assertFalse(request.options.disableNotification)
        }

    private companion object {
        const val BOT = 500L
        const val BOT_CHAT = 500L
        const val CHAT = 100L
        const val QUERY_ID = 9_000L
    }
}
